# brave-instrumentation-rpc rationale

## `RpcRequest` overview

The `RpcRequest` type is a model adapter, used initially for sampling, but in
the future can be used to parse data into the span model, for example to
portably create a span name without special knowledge of the RPC library. This
is repeating the work we did in HTTP, which allows allows library agnostic
data and sampling policy.

## `RpcRequest` initially with `method()` and `service()` properties

### Why in general?
In order to lower initial effort, we decided to scope the initial
implementation of RPC to just things needed by samplers, injection and
extraction. Among these were the desire to create a sampling rule to match
requests to an "auth service" or a "play" method.

## Examples please?
`rpc.method` - The unqualified, case-sensitive method name. Prefer the name
defined in IDL to any mapped Java method name.

Examples
* gRPC - full method "grpc.health.v1.Health/Check" returns "Check"
* Apache Dubbo - "demo.service.DemoService#sayHello()" command returns "demo.service.DemoService"
* Apache Thrift - full method "scribe.Log" returns "Log"

`rpc.service` - The fully-qualified, case-sensitive service path. Prefer the
name defined in IDL to any mapped Java package name.

Examples
* gRPC - full method "grpc.health.v1.Health/Check" returns "grpc.health.v1.Health"
* Apache Dubbo - "demo.service.DemoService#sayHello()" command returns "demo.service.DemoService"
* Apache Thrift - full method "scribe.Log" returns "scribe"

### Why not parameters?
It was easier to start with these because the `method` and `service` properties
of RPC are quite explored and also easy compared to other attributes such as
request parameters. For example, unlike HTTP, where parameters are strings, RPC
parameters can be encoded in binary types, such as protobuf. Not only does this
present abstraction concerns, such as where in the code these types are
unmarshalled, but also serialization issues, when we consider most policy will
be defined declaratively.

### Why use `rpc.method` and `rpc.service`?
To reduce friction, we wanted to use names already in practice. For example,
`method` and `service` are fairly common in IDL such as Apache Thrift and gRPC.
These names are also used in non-IDL based RPC, such as Apache Dubbo. Moreover,
there are established practice of `method` and `service` in tools such as
[Prometheus gRPC monitoring](https://github.com/grpc-ecosystem/go-grpc-prometheus#labels).

While there could be confusion around `rpc.method` vs Java method, that exists
anyway and is easy to explain: When IDL exists, prefer its service and method
name to any matched Java names. 

`rpc.service` unfortunately conflicts with our term `Endpoint.serviceName`, but
only if you ignore the `Endpoint` prefix. Given this, we feel it fits in with
existing practice and doesn't add enough confusion to create new terms.

### Wait.. not everything has a `service` namespace?

Not all RPC implementation has a service/namespace. For example, Redis commands
have a flat namespace. In our model, not applicable properties are represented
as null. The important part is decoupling `service` from `method` allows one
property, `method`, to be extractable more frequently vs if we forced
concatenation through a `full method name` as exists in gRPC.

### But what if I want a `full method name` as exists in gRPC?

We can have the talk about what an idiomatic term could be for a fully
qualified method name. Only gRPC uses the term `full method name`, so it isn't
as clean to lift that term up, vs other names such as `service` that exist in
multiple tools such as Apache Thrift.

Meanwhile, in gRPC, the full method name can be composed using `Matchers.and()`
```java
rpcTracing = rpcTracingBuilder.serverSampler(RpcRuleSampler.newBuilder()
  .putRule(and(serviceEquals("users.UserService"), methodEquals("GetUserToken")), RateLimitingSampler.create(100))
  .build()).build();

grpcTracing = GrpcTracing.create(rpcTracing);
```

### But my span name is already what I want!
It is subtle, but important to note that `RpcRequest` is an input type used in
parameterized sampling. This happens before a span name is chosen. Moreover, a
future change will allow parsing into a span name from the same type. In other
words, `RpcRequest` is an intermediate model that must evaluate properties
before a span exists.

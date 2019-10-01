# brave-instrumentation-rpc rationale

## `RpcRequest` initially with `method()` and `service()` properties

### Why in general?
In order to lower initial effort, we decided to scope the initial
implementation of RPC to just things needed by samplers, injection and
extraction. Among these were the desire to create a sampling rule to match
requests to an "auth service" or a "play" method.

## Examples please?
`rpc.method` - The unqualified, case-sensitive method name, defined in IDL or
the corresponding protocol.

Examples
* gRPC - full method "grpc.health.v1.Health/Check" returns "Check"
* Apache Thrift - full method "scribe.Log" returns "Log"
* Redis - "EXISTS" command returns "EXISTS"

`rpc.service` - The fully-qualified, case-sensitive service path as defined in IDL.

Examples
* gRPC - full method "grpc.health.v1.Health/Check" returns "grpc.health.v1.Health"
* Apache Thrift - full method "scribe.Log" returns "scribe"
* Redis - "EXISTS" command returns null

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
Moreover, there are established practice of `method` and `service` in tools
such as [Prometheus gRPC monitoring](https://github.com/grpc-ecosystem/go-grpc-prometheus#labels).

While there could be confusion around `rpc.method` vs Java method, that exists
anyway and is easy to explain. `rpc.service` unfortunately conflicts with our
term `Endpoint.serviceName`, but only if you ignore the `Endpoint` prefix.
Given this, we feel it is conventional, fits in with existing practice and
doesn't add enough confusion to create new terms.

### Wait.. not everything has a `service` namespace?

Not all RPC implementation has a service/namespace. For example, Redis commands
have a flat namespace. In our model, not applicable properties are represented
as null. The important part is decoupling `service` from `method` allows one
property, `method`, to be extractable more frequently vs if we forced
concatenation through a `full method name` as exists in gRPC.

### But what if I want a `full method name` as exists in gRPC?

We can have the talk about what an idiomatic term could be for a fully
qualified method name. Only gRPC uses the term `full method name`, so it isn't
as clean to lift that term up, vs other names such as `service` than exist in
multiple tools such as Apache Thrift. For now, concat on your own.

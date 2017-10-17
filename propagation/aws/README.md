# brave-propagation-aws
This changes brave to use "x-amzn-trace-id" as opposed to "x-b3" prefixed headers to propagate trace
context across processes.

To enable this, configure `brave.Tracing` with `AWSPropagation.FACTORY` like so:

```java
tracing = Tracing.newBuilder()
    .propagationFactory(AWSPropagation.FACTORY)
    ...
    .build();
```

## Notes
* This does not send spans to Amazon. If you want to do that, use [io.zipkin.aws:reporter-xray-udp](https://github.com/openzipkin/zipkin-aws).
  * Unless you send spans to amazon, the impact is only which headers are used by Brave.
* This neither depends on, nor coordinates with [com.amazonaws:aws-xray-recorder-sdk-core](http://docs.aws.amazon.com/xray/latest/devguide/xray-sdk-java.html).
  * If you are using Amazon X-Ray SDK in the same app, Brave will not "see" those traces and visa versa.
  * If you would like X-Ray SDK integration (such that traces are mutually visible), please raise an issue.
* This implicitly switches Brave to use 128-bit trace IDS
  * Internally, Amazon's root timestamp is encoded in the first 32-bits of the 128-bit trace ID.

## Utilities
There are a couple added utilities for parsing and generating an AWS trace ID string:

* `AWSPropagation.traceId` - used for correlation purposes and lookup in the X-Ray UI
* `AWSPropagation.extract` - extracts a trace context from a string such as an environment variable.
* `AWSPropagation.extractLambda` - special form of extract which reads from the standard lambda env.

For example, if you are in a lambda environment, you can read the incoming context like this:
```java
span = tracer.nextSpan(AWSPropagation.extractLambda());
```

## Extra fields
Amazon's trace ID format allows propagation of "extra" fields. For example, someone can add
diagnostic variables by appending them to the trace header as discussed in the [ALB blog](https://aws.amazon.com/blogs/aws/application-performance-percentiles-and-request-tracing-for-aws-application-load-balancer/).

Ex: the below header includes a custom field `CalledFrom=Foo`, which is non-standard, but will be
propagated throughout the trace.
```
X-Amzn-Trace-Id: Root=1-58211399-36d228ad5d99923122bbe354;CalledFrom=Foo
```

Internally, this field is stored in `TraceContext.extra()`, which allows it to be carried from the
point a trace context is extracted until where it is injected into an outgoing request or message.

# brave-propagation-aws
This changes brave to use "x-amzn-trace-id" as opposed to "x-b3" prefixed headers to propagate trace
context across processes.

To enable this, configure `brave.Tracing` with `AWSPropagation.Factory` like so:

```java
tracing = Tracing.newBuilder()
    .propagationFactory(new AWSPropagation.Factory())
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

* `AWSPropagation.traceIdString` - used to generate a formatted trace ID for correlation purposes.
* `AWSPropagation.extract` - extracts a trace context from a string such as an environment variable.

Ex to extract the trace ID from the built-in AWS Lambda variable
```java
extracted = AWSPropagation.extract(System.getenv("_X_AMZN_TRACE_ID"));
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

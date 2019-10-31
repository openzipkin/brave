# brave-instrumentation-messaging

This is a helper for messaging (queue or topic) libraries such as JMS and
Kafka. Specifically, this includes samplers for producers and consumers,
configured with `MessagingTracing`.

The `MessagingTracing` class holds a reference to a tracing component and
sampling policy.

## Sampling Policy
The default sampling policy is to use the default (trace ID) sampler for
consumer and producer requests.

For example, if there's a incoming message that has no trace IDs in its
headers, the sampler indicated by `MessagingTracing.Builder.consumerSampler`
decides whether or not to start a new trace. Once a trace is in progress, it is
used for any outgoing messages (producer requests).

On the other hand, you may have outgoing messages didn't originate from a
server or message consumer. For example, bootstrapping your application might
send a message to an infrastructure service. In this case, the policy defined
by `MessagingTracing.Builder.producerSampler` decides if a new trace will be
started or not.

You can change the sampling policy by specifying it in the `MessagingTracing`
component. The default implementation `MessagingRuleSampler` allows you to
declare rules based on declare rules based on messaging properties and apply an
appropriate sampling rate.

Ex. Here's a sampler that traces 100 consumer requests per second, except for
the "alerts" channel. Other requests will use a global rate provided by the
`Tracing` component.

```java
import brave.sampler.Matchers;

import static brave.messaging.MessagingRequestMatchers.channelNameEquals;

messagingTracingBuilder.consumerSampler(MessagingRuleSampler.newBuilder()
  .putRule(channelNameEquals("alerts"), Sampler.NEVER_SAMPLE)
  .putRule(Matchers.alwaysMatch(), RateLimitingSampler.create(100))
  .build());
```

# Developing new instrumentation

Check for [instrumentation written here](../) and [Zipkin's list](https://zipkin.io/pages/tracers_instrumentation.html)
before rolling your own Messaging instrumentation! Besides documentation here,
you should look at the [core library documentation](../../brave/README.md) as it
covers topics including propagation. You may find our [feature tests](src/test/java/brave/messaging/features) helpful, too.

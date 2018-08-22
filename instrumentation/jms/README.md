# Brave JMS instrumentation
This module provides instrumentation for JMS 1.1 or 2.0 consumers,
producers and listeners.

Under the scenes:
* `TracingMessageProducer` - completes a producer span per message and propagates it via headers.
* `TracingMessageConsumer` - completes a consumer span on `receive`, resuming a trace in headers if present.
* `TracingMessageListener` - does the same as `TracingMessageConsumer`, and times the user-supplied listener.

## Setup
First, setup the generic Jms component like this:
```java
jmsTracing = JmsTracing.newBuilder(tracing)
                       .remoteServiceName("my-broker")
                       .build();
```

To use the producer simply wrap it like this:
```java
MessageProducer messageProducer = session.createProducer(queue);
MessageProducer tracingMessageProducer = jmsTracing.producer(messageProducer);

// You'll notice "b3" as a message property
tracingMessageProducer.send(message);
```

Same goes for the consumer:
```java
MessageConsumer messageConsumer = session.createConsumer(queue);
MessageConsumer tracingMessageConsumer = jmsTracing.consumer(messageConsumer);

// If you are using message listeners, your work will be traced based on incoming "b3" headers
tracingMessageConsumer.setListener(myListener);
```

## What's happening?
Typically, there are three spans involved in message tracing:
* If a message producer is traced, it completes a PRODUCER span per message
* If a consumer is traced, receive completes a CONSUMER span based on the incoming message or topic
* Message listener timing is in a child of the CONSUMER span.

## Custom Message Processing

If you are using Jms `MessageListener`, the following is automatic. If
you have custom code, or are using a different processing abstraction,
read below:

When ready for processing use `JmsTracing.nextSpan` to continue the trace.

```java
// Typically, poll is in a loop. the consumer is wrapped
while (running) {
  Message message = tracingMessageConsumer.receive();
  // either automatically or manually wrap your real process() method to use jmsTracing.nextSpan()
  messages.forEach(message -> process(message));
}
```

If you are in a position where you have a custom processing loop, you can do something like this
to trace manually or you can do similar via automatic instrumentation like AspectJ.
```java
void process(Message message) {
  // Grab any span from the message. The topic and key are automatically tagged
  Span span = jmsTracing.nextSpan(message).name("process").start();

  // Below is the same setup as any synchronous tracing
  try (SpanInScope ws = tracer.withSpanInScope(span)) { // so logging can see trace ID
    return doProcess(message); // do the actual work
  } catch (RuntimeException | Error e) {
    span.error(e); // make sure any error gets into the span before it is finished
    throw e;
  } finally {
    span.finish(); // ensure the span representing this processing completes.
  }
}
```

## Notes
* This instrumentation library works with JMS 1.1 and 2.0
* More information about "Message Tracing" [here](https://github.com/openzipkin/openzipkin.github.io/blob/master/pages/instrumenting.md#message-tracing)

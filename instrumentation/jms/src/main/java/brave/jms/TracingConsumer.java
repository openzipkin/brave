package brave.jms;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.internal.Nullable;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContextOrSamplingFlags;
import javax.jms.Destination;
import javax.jms.Message;

abstract class TracingConsumer<C> {
  final C delegate;
  final JmsTracing jmsTracing;
  final Tracing tracing;
  final Tracer tracer;
  final Extractor<Message> extractor;
  @Nullable final String remoteServiceName;

  TracingConsumer(C delegate, JmsTracing jmsTracing) {
    this.delegate = delegate;
    this.jmsTracing = jmsTracing;
    this.tracing = jmsTracing.tracing;
    this.tracer = tracing.tracer();
    this.extractor = jmsTracing.extractor;
    this.remoteServiceName = jmsTracing.remoteServiceName;
  }

  void handleReceive(Message message) {
    if (message == null || tracing.isNoop()) return;
    // remove prior propagation headers from the message
    TraceContextOrSamplingFlags extracted = jmsTracing.extractAndClearMessage(message);
    Span span = tracer.nextSpan(extracted);
    if (!span.isNoop()) {
      span.name("receive").kind(Span.Kind.CONSUMER);
      Destination destination = destination(message);
      if (destination != null) jmsTracing.tagQueueOrTopic(destination, span);
      if (remoteServiceName != null) span.remoteServiceName(remoteServiceName);

      // incur timestamp overhead only once
      long timestamp = tracing.clock(span.context()).currentTimeMicroseconds();
      span.start(timestamp).finish(timestamp);
    }
    jmsTracing.setNextParent(message, span.context());
  }

  abstract @Nullable Destination destination(Message message);
}

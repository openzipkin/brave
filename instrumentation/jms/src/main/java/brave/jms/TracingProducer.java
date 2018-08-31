package brave.jms;

import brave.Span;
import brave.Tracer;
import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import javax.jms.Destination;

abstract class TracingProducer<P, M> {

  final P delegate;
  final JmsTracing jmsTracing;
  final Tracer tracer;
  final CurrentTraceContext current;
  @Nullable final String remoteServiceName;

  TracingProducer(P delegate, JmsTracing jmsTracing) {
    this.delegate = delegate;
    this.jmsTracing = jmsTracing;
    this.tracer = jmsTracing.tracing.tracer();
    this.current = jmsTracing.tracing.currentTraceContext();
    this.remoteServiceName = jmsTracing.remoteServiceName;
  }

  Span createAndStartProducerSpan(Destination destination, M message) {
    TraceContext maybeParent = current.get();
    // Unlike message consumers, we try current span before trying extraction. This is the proper
    // order because the span in scope should take precedence over a potentially stale header entry.
    //
    // NOTE: Brave instrumentation used properly does not result in stale header entries, as we
    // always clear message headers after reading.
    Span span;
    if (maybeParent == null) {
      span = tracer.nextSpan(extractAndClearMessage(message));
    } else {
      // As JMS is sensitive about write access to headers, we  defensively clear even if it seems
      // upstream would have cleared (because there is a span in scope!).
      span = tracer.newChild(maybeParent);
      clearPropagationHeaders(message);
    }

    if (!span.isNoop()) {
      span.kind(Span.Kind.PRODUCER).name("send");
      if (destination == null) destination = destination(message);
      if (destination != null) jmsTracing.tagQueueOrTopic(destination, span);
      if (remoteServiceName != null) span.remoteServiceName(remoteServiceName);
      span.start();
    }

    addB3SingleHeader(message, span.context());
    return span;
  }

  abstract void addB3SingleHeader(M message, TraceContext context);

  abstract void clearPropagationHeaders(M message);

  abstract TraceContextOrSamplingFlags extractAndClearMessage(M message);

  @Nullable abstract Destination destination(M message);
}

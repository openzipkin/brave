package brave.messaging;

import brave.Span;
import brave.Tracer;
import brave.propagation.TraceContext;

public class MessagingProducerHandler<Msg> extends MessagingHandler<Msg, MessagingAdapter<Msg>> {

  public static <Msg> MessagingProducerHandler<Msg> create(MessagingTracing tracing,
      MessagingAdapter<Msg> adapter,
      TraceContext.Extractor<Msg> extractor,
      TraceContext.Injector<Msg> injector) {
    return new MessagingProducerHandler<>(tracing, adapter, extractor, injector);
  }

  final Tracer tracer;

  MessagingProducerHandler(MessagingTracing messagingTracing,
      MessagingAdapter<Msg> adapter,
      TraceContext.Extractor<Msg> extractor,
      TraceContext.Injector<Msg> injector) {
    super(messagingTracing.tracing.currentTraceContext(), adapter, messagingTracing.parser,
        extractor, injector);
    this.tracer = messagingTracing.tracing.tracer();
  }

  public Span handleProduce(Msg message) {
    TraceContext maybeParent = currentTraceContext.get();
    // Unlike message consumers, we try current span before trying extraction. This is the proper
    // order because the span in scope should take precedence over a potentially stale header entry.
    //
    // NOTE: Brave instrumentation used properly does not result in stale header entries, as we
    // always clear message headers after reading.
    Span span;
    if (maybeParent == null) {
      span = tracer.nextSpan(parser.extractContextAndClearMessage(adapter, extractor, message));
    } else {
      // As JMS is sensitive about write access to headers, we  defensively clear even if it seems
      // upstream would have cleared (because there is a span in scope!).
      span = tracer.newChild(maybeParent);
      adapter.clearPropagation(message);
    }

    if (!span.isNoop()) {
      span.kind(Span.Kind.PRODUCER).name(adapter.operation(message));
      parser.message(adapter, message, span);
      String remoteServiceName = adapter.remoteServiceName(message);
      if (remoteServiceName != null) span.remoteServiceName(remoteServiceName);
      span.start();
    }

    injector.inject(span.context(), message);

    return span;
  }
}

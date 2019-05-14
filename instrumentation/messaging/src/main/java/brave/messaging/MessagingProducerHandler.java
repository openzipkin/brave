package brave.messaging;

import brave.Span;
import brave.Tracer;
import brave.propagation.TraceContext;

public class MessagingProducerHandler<P, Chan, Msg>
    extends MessagingHandler<Chan, Msg, ChannelAdapter<Chan>, MessageAdapter<Msg>> {

  public static <P, Chan, Msg> MessagingProducerHandler<P, Chan, Msg> create(
      P delegate,
      MessagingTracing tracing,
      ChannelAdapter<Chan> channelAdapter,
      MessageAdapter<Msg> messageAdapter,
      TraceContext.Extractor<Msg> extractor,
      TraceContext.Injector<Msg> injector) {
    return new MessagingProducerHandler<>(delegate, tracing, channelAdapter, messageAdapter,
        extractor, injector);
  }

  public final P delegate;
  final Tracer tracer;

  public MessagingProducerHandler(
      P delegate,
      MessagingTracing messagingTracing,
      ChannelAdapter<Chan> channelAdapter,
      MessageAdapter<Msg> messageAdapter,
      TraceContext.Extractor<Msg> extractor,
      TraceContext.Injector<Msg> injector) {
    super(messagingTracing.tracing.currentTraceContext(), channelAdapter, messageAdapter,
        messagingTracing.parser, extractor, injector);
    this.delegate = delegate;
    this.tracer = messagingTracing.tracing.tracer();
  }

  public Span handleProduce(Chan channel, Msg message) {
    TraceContext maybeParent = currentTraceContext.get();
    // Unlike message consumers, we try current span before trying extraction. This is the proper
    // order because the span in scope should take precedence over a potentially stale header entry.
    //
    // NOTE: Brave instrumentation used properly does not result in stale header entries, as we
    // always clear message headers after reading.
    Span span;
    if (maybeParent == null) {
      span =
          tracer.nextSpan(parser.extractContextAndClearMessage(messageAdapter, extractor, message));
    } else {
      // As JMS is sensitive about write access to headers, we  defensively clear even if it seems
      // upstream would have cleared (because there is a span in scope!).
      span = tracer.newChild(maybeParent);
      messageAdapter.clearPropagation(message);
    }

    if (!span.isNoop()) {
      span.kind(Span.Kind.PRODUCER).name(messageAdapter.operation(message));
      parser.message(messageAdapter, message, span);
      String remoteServiceName = channelAdapter.remoteServiceName(channel);
      if (remoteServiceName != null) span.remoteServiceName(remoteServiceName);
      span.start();
    }

    injector.inject(span.context(), message);

    return span;
  }
}

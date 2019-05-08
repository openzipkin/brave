package brave.messaging;

import brave.Span;
import brave.SpanCustomizer;
import brave.Tracing;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.List;
import java.util.Map;

public class MessagingConsumerHandler<Msg> extends MessagingHandler<Msg, MessagingAdapter<Msg>> {

  static public <Msg> MessagingConsumerHandler<Msg> create(MessagingTracing tracing,
      MessagingAdapter<Msg> adapter,
      TraceContext.Extractor<Msg> extractor,
      TraceContext.Injector<Msg> injector) {
    return new MessagingConsumerHandler<>(tracing, adapter, extractor, injector);
  }

  final Tracing tracing;

  MessagingConsumerHandler(MessagingTracing messagingTracing,
      MessagingAdapter<Msg> adapter,
      TraceContext.Extractor<Msg> extractor,
      TraceContext.Injector<Msg> injector) {
    super(messagingTracing.tracing.currentTraceContext(), adapter, messagingTracing.parser,
        extractor, injector);
    this.tracing = messagingTracing.tracing;
  }

  public Span nextSpan(Msg message) {
    TraceContextOrSamplingFlags extracted =
        parser.extractContextAndClearMessage(adapter, extractor, message);
    Span result = tracing.tracer().nextSpan(extracted);
    if (extracted.context() == null && !result.isNoop()) {
      addTags(message, result);
    }
    return result;
  }


  /** When an upstream context was not present, lookup keys are unlikely added */
  void addTags(Msg message, SpanCustomizer result) {
    parser.channel(adapter, message, result);
    parser.identifier(adapter, message, result);
  }

  public Map<String, Span> handleConsume(List<Msg> messages, Map<String, Span> spanForChannel) {
    long timestamp = 0L;
    for (int i = 0, length = messages.size(); i < length; i++) {
      Msg message = messages.get(i);
      TraceContextOrSamplingFlags extracted =
          parser.extractContextAndClearMessage(adapter, extractor, message);

      // If we extracted neither a trace context, nor request-scoped data (extra),
      // make or reuse a span for this topic
      if (extracted.samplingFlags() != null && extracted.extra().isEmpty()) {
        String channel = adapter.channel(message);
        Span span = spanForChannel.get(channel);
        if (span == null) {
          span = tracing.tracer().nextSpan(extracted);
          if (!span.isNoop()) {
            span.name(adapter.operation(message)).kind(Span.Kind.CONSUMER);
            parser.message(adapter, message, span);
            String remoteServiceName = adapter.remoteServiceName(message);
            if (remoteServiceName != null) span.remoteServiceName(remoteServiceName);
            // incur timestamp overhead only once
            if (timestamp == 0L) {
              timestamp = tracing.clock(span.context()).currentTimeMicroseconds();
            }
            span.start(timestamp);
          }
          spanForChannel.put(channel, span);
        }
        injector.inject(span.context(), message);
      } else { // we extracted request-scoped data, so cannot share a consumer span.
        Span span = tracing.tracer().nextSpan(extracted);
        if (!span.isNoop()) {
          span.name(adapter.operation(message)).kind(Span.Kind.CONSUMER);
          parser.message(adapter, message, span);
          String remoteServiceName = adapter.remoteServiceName(message);
          if (remoteServiceName != null) span.remoteServiceName(remoteServiceName);
          // incur timestamp overhead only once
          if (timestamp == 0L) {
            timestamp = tracing.clock(span.context()).currentTimeMicroseconds();
          }
          span.start(timestamp).finish(timestamp); // span won't be shared by other records
        }
        injector.inject(span.context(), message);
      }
    }
    return spanForChannel;
  }
}

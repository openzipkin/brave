package brave.messaging;

import brave.SpanCustomizer;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;

public class MessagingParser {

  public <Msg> void message(MessagingAdapter<Msg> adapter, Msg message,
      SpanCustomizer customizer) {
    customizer.name(adapter.operation(message));
    channel(adapter, message, customizer);
    identifier(adapter, message, customizer);
  }

  public <Msg> void channel(MessagingAdapter<Msg> adapter, Msg message,
      SpanCustomizer customizer) {
    String channel = adapter.channel(message);
    if (channel != null) customizer.tag(adapter.channelTagKey(message), channel);
  }

  public <Msg> void identifier(MessagingAdapter<Msg> adapter, Msg message,
      SpanCustomizer customizer) {
    String identifier = adapter.identifier(message);
    if (identifier != null) {
      customizer.tag(adapter.identifierTagKey(), adapter.identifier(message));
    }
  }

  public <Msg> TraceContextOrSamplingFlags extractContextAndClearMessage(
      MessagingAdapter<Msg> adapter,
      TraceContext.Extractor<Msg> extractor, Msg message) {
    TraceContextOrSamplingFlags extracted = extractContext(extractor, message);
    // clear propagation headers if we were able to extract a span
    if (!extracted.equals(TraceContextOrSamplingFlags.EMPTY)) {
      adapter.clearPropagation(message);
    }
    return extracted;
  }

  private <Msg> TraceContextOrSamplingFlags extractContext(TraceContext.Extractor<Msg> extractor,
      Msg message) {
    return extractor.extract(message);
  }
}

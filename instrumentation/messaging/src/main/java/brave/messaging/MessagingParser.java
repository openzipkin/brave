package brave.messaging;

import brave.SpanCustomizer;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;

public class MessagingParser {

  public <Chan, Msg> void message(ChannelAdapter<Chan> channelAdapter,
      MessageAdapter<Msg> messageAdapter,
      Chan channel, Msg message, SpanCustomizer customizer) {
    customizer.name(messageAdapter.operation(message));
    channel(channelAdapter, channel, customizer);
    identifier(messageAdapter, message, customizer);
  }

  public <Chan> void channel(ChannelAdapter<Chan> adapter, Chan chan,
      SpanCustomizer customizer) {
    String channel = adapter.channel(chan);
    if (chan != null) customizer.tag(adapter.channelTagKey(chan), channel);
  }

  public <Msg> void identifier(MessageAdapter<Msg> adapter, Msg message,
      SpanCustomizer customizer) {
    String identifier = adapter.identifier(message);
    if (identifier != null) {
      customizer.tag(adapter.identifierTagKey(), identifier);
    }
  }

  public <Msg> TraceContextOrSamplingFlags extractContextAndClearMessage(
      MessageAdapter<Msg> adapter,
      TraceContext.Extractor<Msg> extractor,
      Msg message) {
    TraceContextOrSamplingFlags extracted = extractor.extract(message);
    // clear propagation headers if we were able to extract a span
    //TODO check if correct to not filter on empty flags. Diff between kafka and jms instrumentation
    //if (!extracted.equals(TraceContextOrSamplingFlags.EMPTY)) {
      adapter.clearPropagation(message);
    //}
    return extracted;
  }
}

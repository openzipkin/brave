package brave.messaging;

import brave.SpanCustomizer;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;

public class MessagingParser {

    public <Msg> void message(MessagingAdapter<Msg> adapter, Msg message,
        SpanCustomizer customizer) {
        customizer.name(adapter.operation(message));
        channel(adapter, message, customizer);
    }

    public <Msg> void channel(MessagingAdapter<Msg> adapter, Msg message,
        SpanCustomizer customizer) {
        MessagingAdapter.Channel channel = adapter.channel(message);
        if (channel != null) {
            customizer.tag(channel.tagKey(adapter.protocol(message)), channel.name);
        }
    }

    public <Msg> TraceContextOrSamplingFlags extractAndClearMessage(MessagingAdapter<Msg> adapter,
        TraceContext.Extractor<Msg> extractor,
        Msg message) {
        TraceContextOrSamplingFlags extracted = extractor.extract(message);
        // clear propagation headers if we were able to extract a span
        if (!extracted.equals(TraceContextOrSamplingFlags.EMPTY)) {
            adapter.clearPropagation(message);
        }
        return extracted;
    }
}

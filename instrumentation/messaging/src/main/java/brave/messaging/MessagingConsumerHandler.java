package brave.messaging;

import brave.Span;
import brave.Tracing;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;

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

    public Span startConsume(Msg message) {
        if (message == null) return null;
        // remove prior propagation headers from the message
        TraceContextOrSamplingFlags extracted =
            parser.extractAndClearMessage(adapter, extractor, message);
        Span span = tracing.tracer().nextSpan(extracted);
        if (!span.isNoop()) {
            span.name(adapter.operation(message)).kind(Span.Kind.CONSUMER);
            parser.message(adapter, message, span);
            String remoteServiceName = adapter.remoteServiceName(message);
            if (remoteServiceName != null) span.remoteServiceName(remoteServiceName);
        }
        return span;
    }

    public long timestamp(Span span) {
        return tracing.clock(span.context()).currentTimeMicroseconds();
    }

    public Span handleStartAndFinish(Msg message) {
        Span span = startConsume(message);
        // incur timestamp overhead only once
        long timestamp = timestamp(span);
        span.start(timestamp).finish(timestamp);
        return span;
    }

    public Span handleStart(Msg message, long timestamp) {
        Span span = startConsume(message);
        span.start(timestamp);
        return span;
    }

    public Span nextSpan(Msg message) {
        TraceContextOrSamplingFlags extracted =
            parser.extractAndClearMessage(adapter, extractor, message);
        Span result = tracing.tracer().nextSpan(extracted);
        if (extracted.context() == null && !result.isNoop()) {
            parser.channel(adapter, message, result);
        }
        return result;
    }
}

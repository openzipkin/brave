package com.github.kristofa.brave;

import com.github.kristofa.brave.SpanAndEndpoint.LocalSpanAndEndpoint;
import com.github.kristofa.brave.internal.Nullable;
import com.google.auto.value.AutoValue;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Span;
import zipkin.Constants;

import java.util.Random;
import zipkin.reporter.Reporter;

import static com.github.kristofa.brave.internal.DefaultSpanCodec.toZipkin;
import static zipkin.Constants.LOCAL_COMPONENT;

/**
 * Local tracer is designed for in-process activity that explains latency.
 *
 * <p/>For example, a local span could represent bootstrap, codec, file i/o or
 * other activity that notably impacts performance.
 *
 * <p/>Local spans always have a binary annotation "lc" which indicates the
 * component name. Usings zipkin's UI or Api, you can query by for spans that
 * use a component like this: {@code lc=spring-boot}.
 *
 * <p/>Here's an example of allocating precise duration for a local span:
 * <pre>
 * tracer.startNewSpan("codec", "encode");
 * try {
 *   return codec.encode(input);
 * } finally {
 *   tracer.finishSpan();
 * }
 * </pre>
 *
 * @see Constants#LOCAL_COMPONENT
 */
@AutoValue
public abstract class LocalTracer extends AnnotationSubmitter {

    static Builder builder() {
        return new AutoValue_LocalTracer.Builder();
    }

    // visible for testing
    abstract Builder toBuilder();

    @Override
    abstract LocalSpanAndEndpoint spanAndEndpoint();

    abstract Reporter<zipkin.Span> reporter();

    abstract boolean allowNestedLocalSpans();

    abstract Sampler traceSampler();

    @AutoValue.Builder
    abstract static class Builder {

        abstract Builder spanAndEndpoint(LocalSpanAndEndpoint spanAndEndpoint);

        abstract Builder randomGenerator(Random randomGenerator);

        abstract Builder reporter(Reporter<zipkin.Span> reporter);

        abstract Builder allowNestedLocalSpans(boolean allowNestedLocalSpans);

        abstract Builder traceSampler(Sampler sampler);

        abstract Builder clock(Clock clock);

        abstract Builder traceId128Bit(boolean traceId128Bit);

        abstract LocalTracer build();

    }

    /**
     * Request a new local span, which starts now.
     *
     * @param component {@link Constants#LOCAL_COMPONENT component} responsible for the operation
     * @param operation name of the operation that's begun
     * @return metadata about the new span or null if one wasn't started due to sampling policy.
     * @see Constants#LOCAL_COMPONENT
     */
    public SpanId startNewSpan(String component, String operation) {
        return startNewSpan(component, operation, clock().currentTimeMicroseconds());
    }

    /**
     * Request the span that should be considered the new span's parent.
     *
     * If {@link #allowNestedLocalSpans()} is enabled, the new span's parent
     * will be the current local span (if one exists).
     *
     * If nested local spans is not enabled or there is no current local span,
     * the new span's parent will be the current server span (if one exists).
     *
     * @return span that should be the new span's parent, or null if one does not exist.
     */
    @Nullable Span maybeParent() {
        ServerClientAndLocalSpanState state = spanAndEndpoint().state();
        Span parentSpan = null;
        if (allowNestedLocalSpans()) {
            parentSpan = state.getCurrentLocalSpan();
        }

        if (parentSpan == null) {
            ServerSpan currentServerSpan = state.getCurrentServerSpan();
            if (currentServerSpan != null) {
                parentSpan = currentServerSpan.getSpan();
            }
        }

        return parentSpan;
    }

    /**
     * Request a new local span, which started at the given timestamp.
     *
     * @param component {@link Constants#LOCAL_COMPONENT component} responsible for the operation
     * @param operation name of the operation that's begun
     * @param timestamp time the operation started, in epoch microseconds.
     * @return metadata about the new span or null if one wasn't started due to sampling policy.
     * @see Constants#LOCAL_COMPONENT
     */
    public SpanId startNewSpan(String component, String operation, long timestamp) {

        Boolean sample = spanAndEndpoint().state().sample();
        if (Boolean.FALSE.equals(sample)) {
            spanAndEndpoint().state().setCurrentLocalSpan(null);
            return null;
        }

        SpanId nextContext = nextContext(maybeParent());
        if (sample == null) {
            // No sample indication is present.
            if (!traceSampler().isSampled(nextContext.traceId)) {
                spanAndEndpoint().state().setCurrentLocalSpan(null);
                return null;
            }
        }

        Span newSpan = Span.create(nextContext);
        newSpan.setName(operation);
        newSpan.setTimestamp(timestamp);
        newSpan.addToBinary_annotations(
            BinaryAnnotation.create(LOCAL_COMPONENT, component, spanAndEndpoint().endpoint()));
        spanAndEndpoint().state().setCurrentLocalSpan(newSpan);
        return nextContext;
    }

    /**
     * Completes the span, assigning the most precise duration possible.
     */
    public void finishSpan() {
        Span span = spanAndEndpoint().span();
        if (span == null) return;

        long duration = Math.max(1L, clock().currentTimeMicroseconds() - span.getTimestamp());
        internalFinishSpan(span, duration);
    }

    /**
     * Completes the span, which took {@code duration} microseconds.
     */
    public void finishSpan(long duration) {
        Span span = spanAndEndpoint().span();
        if (span == null) return;

        internalFinishSpan(span, duration);
    }

    private void internalFinishSpan(Span span, long duration) {
        synchronized (span) {
            span.setDuration(duration);
        }
        reporter().report(toZipkin(span));
        spanAndEndpoint().state().setCurrentLocalSpan(null);
    }

    LocalTracer() {
    }
}

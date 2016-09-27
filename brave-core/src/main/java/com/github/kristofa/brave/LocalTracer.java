package com.github.kristofa.brave;

import com.github.kristofa.brave.SpanAndEndpoint.LocalSpanAndEndpoint;
import com.github.kristofa.brave.internal.Nullable;
import com.google.auto.value.AutoValue;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Span;
import zipkin.Constants;

import java.util.Random;

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
    static Builder builder(LocalTracer source) {
        return new AutoValue_LocalTracer.Builder(source);
    }

    @Override
    abstract LocalSpanAndEndpoint spanAndEndpoint();

    abstract Random randomGenerator();

    abstract SpanCollector spanCollector();

    abstract boolean allowNestedLocalSpans();

    abstract Sampler traceSampler();

    @Override
    abstract AnnotationSubmitter.Clock clock();

    @AutoValue.Builder
    abstract static class Builder {

        abstract Builder spanAndEndpoint(LocalSpanAndEndpoint spanAndEndpoint);

        abstract Builder randomGenerator(Random randomGenerator);

        abstract Builder spanCollector(SpanCollector spanCollector);

        abstract Builder allowNestedLocalSpans(boolean allowNestedLocalSpans);

        abstract Builder traceSampler(Sampler sampler);

        abstract Builder clock(AnnotationSubmitter.Clock clock);

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
        SpanId spanId = startNewSpan(component, operation, currentTimeMicroseconds(null));
        if (spanId == null) return null;
        Span span = spanAndEndpoint().span();
        synchronized (span) {
            span.startTick = System.nanoTime(); // embezzle start tick into an internal field.
        }
        return spanId;
    }

    private SpanId getNewSpanId() {
        Span parentSpan = getNewSpanParent();
        long newSpanId = randomGenerator().nextLong();
        SpanId.Builder builder = SpanId.builder().spanId(newSpanId);
        if (parentSpan == null) return builder.build(); // new trace
        return builder.traceId(parentSpan.getTrace_id()).parentId(parentSpan.getId()).build();
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
    @Nullable
    Span getNewSpanParent() {
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

        SpanId newSpanId = getNewSpanId();
        if (sample == null) {
            // No sample indication is present.
            if (!traceSampler().isSampled(newSpanId.traceId)) {
                spanAndEndpoint().state().setCurrentLocalSpan(null);
                return null;
            }
        }

        Span newSpan = newSpanId.toSpan();
        newSpan.setName(operation);
        newSpan.setTimestamp(timestamp);
        newSpan.addToBinary_annotations(
            BinaryAnnotation.create(LOCAL_COMPONENT, component, spanAndEndpoint().endpoint()));
        spanAndEndpoint().state().setCurrentLocalSpan(newSpan);
        return newSpanId;
    }

    /**
     * Completes the span, assigning the most precise duration possible.
     */
    public void finishSpan() {
        long endTick = System.nanoTime();

        Span span = spanAndEndpoint().span();
        if (span == null) return;

        Long startTick;
        synchronized (span) {
            startTick = span.startTick;
        }
        final long duration;
        if (startTick != null) {
            duration = Math.max(1L, (endTick - startTick) / 1000L);
        } else {
            duration = Math.max(1L, currentTimeMicroseconds(null) - span.getTimestamp());
        }
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
            spanCollector().collect(span);
        }

        spanAndEndpoint().state().setCurrentLocalSpan(null);
    }

    LocalTracer() {
    }
}

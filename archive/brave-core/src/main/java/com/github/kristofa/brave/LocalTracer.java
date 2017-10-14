package com.github.kristofa.brave;

import com.github.kristofa.brave.internal.Nullable;
import com.google.auto.value.AutoValue;
import com.twitter.zipkin.gen.Span;
import zipkin.Constants;

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
 * @deprecated Replaced by {@code brave.Span}
 */
@Deprecated
@AutoValue
public abstract class LocalTracer extends AnnotationSubmitter {

    abstract ServerSpanThreadBinder currentServerSpan();

    @Override abstract LocalSpanThreadBinder currentSpan();

    abstract boolean allowNestedLocalSpans();

    abstract SpanFactory spanFactory();

    @AutoValue.Builder
    abstract static class Builder {
        abstract Builder spanFactory(SpanFactory spanFactory);

        abstract Builder currentServerSpan(ServerSpanThreadBinder currentServerSpan);

        abstract Builder currentSpan(LocalSpanThreadBinder currentSpan);

        abstract Builder recorder(Recorder recorder);

        abstract Builder allowNestedLocalSpans(boolean allowNestedLocalSpans);

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
        return startNewSpan(component, operation, recorder().currentTimeMicroseconds());
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
    @Nullable SpanId maybeParent() {
        Span parentSpan = null;
        if (allowNestedLocalSpans()) {
            parentSpan = currentSpan().get();
        }

        if (parentSpan == null) {
            Span currentServerSpan = currentServerSpan().get();
            if (currentServerSpan != null) {
                parentSpan = currentServerSpan;
            }
        }
        if (parentSpan == null) return null;
        return Brave.context(parentSpan);
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
        // When a trace context is extracted from an incoming request, it may have only the
        // sampled header (no ids). If the header says unsampled, we must honor that. Since
        // we currently don't synthesize a fake span when a trace is unsampled, we have to
        // check sampled state explicitly.
        Boolean sample = currentServerSpan().sampled();
        if (Boolean.FALSE.equals(sample)) {
            currentSpan().setCurrentSpan(null);
            return null;
        }

        Span span = spanFactory().nextSpan(maybeParent());
        SpanId context = Brave.context(span);
        if (Boolean.FALSE.equals(context.sampled())) {
            currentSpan().setCurrentSpan(null);
            return null;
        }

        recorder().start(span, timestamp);
        recorder().name(span, operation);
        recorder().tag(span, LOCAL_COMPONENT, component);

        currentSpan().setCurrentSpan(span);
        return context;
    }

    /**
     * Completes the span, assigning the most precise duration possible.
     */
    public void finishSpan() {
        Span span = currentSpan().get();
        if (span == null) return;
        recorder().finish(span, recorder().currentTimeMicroseconds());
        currentSpan().setCurrentSpan(null);
    }

    /**
     * Completes the span, which took {@code duration} microseconds.
     */
    public void finishSpan(long duration) {
        Span span = currentSpan().get();
        if (span == null) return;

        Long timestamp = recorder().timestamp(span);
        if (timestamp == null) {
            recorder().flush(span);
        } else {
            recorder().finish(span, timestamp + duration);
        }
        currentSpan().setCurrentSpan(null);
    }

    LocalTracer() {
    }
}

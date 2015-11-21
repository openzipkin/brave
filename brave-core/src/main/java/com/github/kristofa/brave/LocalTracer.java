package com.github.kristofa.brave;

import com.google.auto.value.AutoValue;
import com.twitter.zipkin.gen.Span;
import com.twitter.zipkin.gen.zipkinCoreConstants;

import static com.twitter.zipkin.gen.zipkinCoreConstants.LOCAL_COMPONENT;

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
 * tracer.startSpan("codec", "encode");
 * try {
 *   return codec.encode(input);
 * } finally {
 *   tracer.finishSpan();
 * }
 * </pre>
 *
 * @see zipkinCoreConstants#LOCAL_COMPONENT
 */
@AutoValue
public abstract class LocalTracer {

    abstract ClientTracer clientTracer();
    abstract SpanCollector spanCollector();
    abstract ServerAndClientSpanState state();

    static LocalTracer create(ClientTracer clientTracer, SpanCollector spanCollector, ServerAndClientSpanState state) {
        return new AutoValue_LocalTracer(clientTracer, spanCollector, state);
    }

    /**
     * Request a new local span, which starts now.
     *
     * @param component {@link zipkinCoreConstants#LOCAL_COMPONENT component} responsible for the operation
     * @param operation name of the operation that's begun
     * @return metadata about the new span or null if one wasn't started due to sampling policy.
     * @see zipkinCoreConstants#LOCAL_COMPONENT
     */
    public SpanId startSpan(String component, String operation) {
        SpanId spanId = clientTracer().startNewSpan(operation);
        if (spanId == null) return null;
        clientTracer().submitBinaryAnnotation(LOCAL_COMPONENT, component);
        state().getCurrentClientSpan()
                .setTimestamp(clientTracer().currentTimeMicroseconds())
                .startTick = System.nanoTime(); // embezzle start tick into an internal field.
        return spanId;
    }

    /**
     * Request a new local span, which started at the given timestamp.
     *
     * @param component {@link zipkinCoreConstants#LOCAL_COMPONENT component} responsible for the operation
     * @param operation name of the operation that's begun
     * @param timestamp time the operation started, in epoch microseconds.
     * @return metadata about the new span or null if one wasn't started due to sampling policy.
     * @see zipkinCoreConstants#LOCAL_COMPONENT
     */
    public SpanId startSpan(String component, String operation, long timestamp) {
        SpanId spanId = clientTracer().startNewSpan(operation);
        if (spanId == null) return null;
        clientTracer().submitBinaryAnnotation(LOCAL_COMPONENT, component);
        state().getCurrentClientSpan().setTimestamp(timestamp);
        return spanId;
    }

    /**
     * Associates an event that explains latency with the current system time.
     *
     * @param value A short tag indicating the event, like "ApplicationReady"
     */
    public void submitAnnotation(String value) {
        clientTracer().submitAnnotation(value);
    }

    /**
     * Associates an event that explains latency with a timestamp.
     *
     * @param value     A short tag indicating the event, like "ApplicationReady"
     * @param timestamp microseconds from epoch
     */
    public void submitAnnotation(String value, long timestamp) {
        clientTracer().submitAnnotation(value, timestamp);
    }

    /**
     * Binary annotations are tags applied to a Span to give it context. For
     * example, a key "your_app.version" would let you lookup spans by version.
     *
     * @param key   Name used to lookup spans, such as "your_app.version"
     * @param value String value, should not be <code>null</code>.
     */
    public void submitBinaryAnnotation(String key, String value) {
        clientTracer().submitBinaryAnnotation(key, value);
    }

    /**
     * Completes the span, assigning the most precise duration possible.
     */
    public void finishSpan() {
        long endTick = System.nanoTime();

        Span span = state().getCurrentClientSpan();
        if (span == null) return;

        Long startTick = span.startTick;
        final long duration;
        if (startTick != null) {
            duration = (endTick - startTick) / 1000;
        } else {
            duration = clientTracer().currentTimeMicroseconds() - span.getTimestamp();
        }
        finishSpan(duration);
    }

    /**
     * Completes the span, which took {@code duration} microseconds.
     */
    public void finishSpan(long duration) {
        Span span = state().getCurrentClientSpan();
        if (span == null) return;

        synchronized (span) {
            span.setDuration(duration);
            spanCollector().collect(span);
        }

        state().setCurrentClientSpan(null);
        state().setCurrentClientServiceName(null);
    }

    LocalTracer() {
    }
}

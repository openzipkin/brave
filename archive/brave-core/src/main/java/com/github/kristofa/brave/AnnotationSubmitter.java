package com.github.kristofa.brave;

import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import zipkin.reporter.Reporter;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Used to submit application specific annotations.
 *
 * @author kristof
 * @deprecated Replaced by {@code brave.Span}
 */
@Deprecated
public abstract class AnnotationSubmitter {

    /**
     * This interface is used to make the implementation to AnnotationSubmitter.currentTimeMicroseconds() contextual.
     * The clock is defined by the subclass's implementation of the `clock()` method.
     * A DefaultClock implementation is provided that simply returns `System.currentTimeMillis() * 1000`.
     */
    public interface Clock {
        /**
         * Epoch microseconds used for {@link zipkin.Span#timestamp} and {@link zipkin.Annotation#timestamp}.
         *
         * <p>This should use the most precise value possible. For example, {@code gettimeofday} or multiplying
         * {@link System#currentTimeMillis} by 1000.
         *
         * <p>See <a href="http://zipkin.io/pages/instrumenting.html">Instrumenting a service</a> for more.
         */
        long currentTimeMicroseconds();
    }

    abstract CurrentSpan currentSpan();

    abstract Recorder recorder();

    /**
     * Associates an event that explains latency with the current system time.
     *
     * @param value A short tag indicating the event, like "finagle.retry"
     */
    public void submitAnnotation(String value) {
        Span span = currentSpan().get();
        if (span == null) return;
        recorder().annotate(span, recorder().currentTimeMicroseconds(), value);
    }

    /**
     * Associates an event that explains latency with a timestamp.
     *
     * <p/> This is an alternative to {@link #submitAnnotation(String)}, when
     * you have a timestamp more precise or accurate than {@link System#currentTimeMillis()}.
     *
     * @param value     A short tag indicating the event, like "finagle.retry"
     * @param timestamp microseconds from epoch
     */
    public void submitAnnotation(String value, long timestamp) {
        Span span = currentSpan().get();
        if (span == null) return;
        recorder().annotate(span, timestamp, value);
    }

    /** This adds an annotation that corresponds with {@link Span#getTimestamp()} */
    void submitStartAnnotation(String startAnnotation) {
        Span span = currentSpan().get();
        if (span == null) return;

        long timestamp = recorder().currentTimeMicroseconds();
        recorder().annotate(span, timestamp, startAnnotation);
        recorder().start(span, timestamp);
    }

    /**
     * This adds an annotation that corresponds with {@link Span#getDuration()}, and sends the span
     * for collection.
     *
     * @return true if a span was sent for collection.
     */
    boolean submitEndAnnotation(String finishAnnotation) {
        Span span = currentSpan().get();
        if (span == null) return false;

        long timestamp = recorder().currentTimeMicroseconds();
        recorder().annotate(span, timestamp, finishAnnotation);
        recorder().finish(span, timestamp);
        return true;
    }

    /** Internal api for submitting an address. */
    void submitAddress(String key, Endpoint endpoint) {
        Span span = currentSpan().get();
        if (span == null) return;

        recorder().address(span, key, endpoint);
    }

    /**
     * Binary annotations are tags applied to a Span to give it context. For
     * example, a key "your_app.version" would let you lookup spans by version.
     *
     * @param key Name used to lookup spans, such as "your_app.version"
     * @param value String value, should not be <code>null</code>.
     */
    public void submitBinaryAnnotation(String key, String value) {
        Span span = currentSpan().get();
        if (span == null) return;
        recorder().tag(span, key, value);
    }

    /** @deprecated use {@link #submitBinaryAnnotation(String, String)} */
    @Deprecated
    public final void submitBinaryAnnotation(String key, int value) {
        // Zipkin v1 UI and query only support String annotations.
        submitBinaryAnnotation(key, String.valueOf(value));
    }

    AnnotationSubmitter() {
    }

    /** Offset-based clock: Uses a single point of reference and offsets to create timestamps. */
    static final class DefaultClock implements Clock {
        // epochMicros is derived by this
        final long createTimestamp;
        final long createTick;

        DefaultClock() {
            createTimestamp = System.currentTimeMillis() * 1000;
            createTick = System.nanoTime();
        }

        /** gets a timestamp based on this the create tick. */
        @Override
        public long currentTimeMicroseconds() {
            return ((System.nanoTime() - createTick) / 1000) + createTimestamp;
        }
    }

    /** @deprecated Please use {@link Brave#serverTracer()} instead. */
    @Deprecated
    public static AnnotationSubmitter create(final SpanAndEndpoint spanAndEndpoint,
        final Clock clock) {
        checkNotNull(spanAndEndpoint, "Null spanAndEndpoint");
        checkNotNull(clock, "Null clock");
        CurrentSpan currentSpan = new CurrentSpan(){
            @Override Span get() {
                return spanAndEndpoint.span();
            }
        };
        Endpoint localEndpoint = spanAndEndpoint.endpoint();
        Recorder recorder = new AutoValue_Recorder_Default(localEndpoint, clock, Reporter.NOOP);
        return create(currentSpan, recorder);
    }

    static AnnotationSubmitter create(final CurrentSpan currentSpan, final Recorder recorder) {
        return new AnnotationSubmitter() {
            @Override CurrentSpan currentSpan() {
                return currentSpan;
            }

            @Override Recorder recorder() {
                return recorder;
            }
        };
    }
}

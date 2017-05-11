package com.github.kristofa.brave;

import com.twitter.zipkin.gen.Span;

/**
 * Collects spans that are submitted by {@link ServerTracer} and {@link ClientTracer}. We can have implementations that
 * simply log the collected spans or implementations that persist the spans to a database, submit them to a service,...
 * 
 * @author kristof
 *
 * @deprecated replaced by {@link zipkin.reporter.Reporter}
 */
@Deprecated
public interface SpanCollector {

    /**
     * Collect span.
     * 
     * @param span Span, should not be <code>null</code>.
     */
    void collect(final Span span);

    /**
     * Adds a fixed annotation that will be added to every span that is submitted to this collector.
     * <p/>
     * One use case for this is to distinguish spans from different environments or for testing reasons.
     * 
     * @param key Annotation name/key. Should not be empty or <code>null</code>.
     * @param value Annotation value. Should not be <code>null</code>.
     *
     * @deprecated decorate {@link #collect(Span)}, if you want to customize spans before they are sent.
     */
    @Deprecated
    void addDefaultAnnotation(final String key, final String value);
}

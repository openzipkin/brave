package com.github.kristofa.brave;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Span;

import static com.github.kristofa.brave.internal.Util.checkNotBlank;
import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Simple {@link SpanCollector} implementation which logs the span through jul at INFO level.
 * <p/>
 * Can be used for testing and debugging.
 * 
 * @author kristof
 */
public class LoggingSpanCollector implements SpanCollector {

    private final Logger logger;
    private final Set<BinaryAnnotation> defaultAnnotations = new LinkedHashSet<BinaryAnnotation>();

    public LoggingSpanCollector() {
        logger = Logger.getLogger(LoggingSpanCollector.class.getName());
    }

    public LoggingSpanCollector(String loggerName) {
        checkNotBlank(loggerName, "Null or blank loggerName");
        logger = Logger.getLogger(loggerName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void collect(final Span span) {
        checkNotNull(span, "Null span");
        if (!defaultAnnotations.isEmpty()) {
            for (final BinaryAnnotation ba : defaultAnnotations) {
                span.addToBinary_annotations(ba);
            }
        }

        if (getLogger().isLoggable(Level.INFO)) {
            getLogger().info(span.toString());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addDefaultAnnotation(final String key, final String value) {
        defaultAnnotations.add(BinaryAnnotation.create(key, value, null));
    }

    Logger getLogger() {
        return logger;
    }

}

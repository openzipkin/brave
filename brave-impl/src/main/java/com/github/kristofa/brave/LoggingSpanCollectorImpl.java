package com.github.kristofa.brave;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.twitter.zipkin.gen.AnnotationType;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Span;

/**
 * Simple {@link SpanCollector} implementation which logs the span through slf4j at INFO level.
 * <p/>
 * Can be used for testing and debugging.
 * 
 * @author kristof
 */
public class LoggingSpanCollectorImpl implements SpanCollector {

    private static final String UTF_8 = "UTF-8";

    private final static Logger LOGGER = LoggerFactory.getLogger(LoggingSpanCollectorImpl.class);
    private final Set<BinaryAnnotation> defaultAnnotations = new HashSet<BinaryAnnotation>();

    /**
     * {@inheritDoc}
     */
    @Override
    public void collect(final Span span) {
        Validate.notNull(span);
        if (!defaultAnnotations.isEmpty()) {
            for (final BinaryAnnotation ba : defaultAnnotations) {
                span.addToBinary_annotations(ba);
            }
        }

        getLogger().info(span.toString());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        // Nothing to do for this collector.

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addDefaultAnnotation(final String key, final String value) {
        Validate.notEmpty(key);
        Validate.notNull(value);

        try {
            final ByteBuffer bb = ByteBuffer.wrap(value.getBytes(UTF_8));

            final BinaryAnnotation binaryAnnotation = new BinaryAnnotation();
            binaryAnnotation.setKey(key);
            binaryAnnotation.setValue(bb);
            binaryAnnotation.setAnnotation_type(AnnotationType.STRING);
            defaultAnnotations.add(binaryAnnotation);

        } catch (final UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    Logger getLogger() {
        return LOGGER;
    }

}

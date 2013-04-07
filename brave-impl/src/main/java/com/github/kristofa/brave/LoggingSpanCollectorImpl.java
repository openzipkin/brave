package com.github.kristofa.brave;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple {@link SpanCollector} implementation which logs the span through slf4j at INFO level.
 * 
 * @author kristof
 */
class LoggingSpanCollectorImpl implements SpanCollector {

    private final static Logger LOGGER = LoggerFactory.getLogger(LoggingSpanCollectorImpl.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public void collect(final Span span) {
        Validate.notNull(span);
        LOGGER.info(span.toString());
    }

}

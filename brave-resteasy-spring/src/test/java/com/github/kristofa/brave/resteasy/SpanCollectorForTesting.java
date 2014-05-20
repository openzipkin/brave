package com.github.kristofa.brave.resteasy;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.kristofa.brave.SpanCollector;
import com.twitter.zipkin.gen.Span;

public class SpanCollectorForTesting implements SpanCollector {

    private final static Logger LOGGER = LoggerFactory.getLogger(SpanCollectorForTesting.class);

    private final List<Span> spans = new ArrayList<Span>();

    private static SpanCollectorForTesting INSTANCE;

    private SpanCollectorForTesting() {

    }

    public static synchronized SpanCollectorForTesting getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new SpanCollectorForTesting();
        }
        return INSTANCE;
    }

    @Override
    public void collect(final Span span) {
        LOGGER.info(span.toString());
        spans.add(span);
    }

    public List<Span> getCollectedSpans() {
        return spans;
    }

    @Override
    public void addDefaultAnnotation(final String key, final String value) {
        // TODO Auto-generated method stub

    }

    @Override
    public void close() {
        // TODO Auto-generated method stub

    }

}

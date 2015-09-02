package com.github.kristofa.brave.spring;

import com.github.kristofa.brave.SpanCollector;
import com.twitter.zipkin.gen.Span;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class SpanCollectorForTesting implements SpanCollector {

    private final static Logger LOGGER = Logger.getLogger(SpanCollectorForTesting.class.getName());

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

    public void clear() {
        spans.clear();
    }

}

package com.github.kristofa.brave.cxf;

import com.github.kristofa.brave.SpanCollector;
import com.twitter.zipkin.gen.Span;

import java.util.ArrayList;
import java.util.List;

public class SpanCollectorForTesting implements SpanCollector {

    private final List<Span> spans = new ArrayList<>();

    @Override
    public void collect(final Span span) {
        spans.add(span);
    }

    public List<Span> getCollectedSpans() {
        return spans;
    }

    @Override
    public void addDefaultAnnotation(final String key, final String value) {
        throw new UnsupportedOperationException();
    }
}
package com.github.kristofa.brave.jersey2;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import zipkin.Span;
import zipkin.reporter.Reporter;

public class ReporterForTesting implements Reporter<Span> {

    private final static Logger LOGGER = Logger.getLogger(ReporterForTesting.class.getName());

    private final List<Span> spans = new ArrayList<Span>();

    private static ReporterForTesting INSTANCE;

    private ReporterForTesting() {

    }

    public static synchronized ReporterForTesting getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ReporterForTesting();
        }
        return INSTANCE;
    }

    public List<Span> getCollectedSpans() {
        return spans;
    }

    @Override
    public void report(Span span) {
        LOGGER.info(span.toString());
        spans.add(span);
    }
}

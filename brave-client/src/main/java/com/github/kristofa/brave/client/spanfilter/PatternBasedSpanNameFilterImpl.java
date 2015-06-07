package com.github.kristofa.brave.client.spanfilter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Given a list of span name patterns like "/api/{client}/{operation}" or "/api/{version}/{client}/save",
 * this filter will only allow these span names to be set as the client span name (else "[span name not defined]").
 */
public class PatternBasedSpanNameFilterImpl implements SpanNameFilter {
    static final String DEFAULT_SPAN_NAME = "[span name not defined]";

    private final Iterable<SpanNamePattern> spanNamePatterns;
    private final String defaultSpanName;

    public PatternBasedSpanNameFilterImpl(Iterable<String> spanNamePatterns) {
        this(spanNamePatterns, DEFAULT_SPAN_NAME);
    }

    public PatternBasedSpanNameFilterImpl(Iterable<String> spanNamePatterns, String defaultSpanName) {
        final List<SpanNamePattern> patternNamePairs = new ArrayList<>();
        if (spanNamePatterns != null) {
            for (String spanNamePattern : spanNamePatterns) {
                if (spanNamePattern != null) {
                    patternNamePairs.add(new SpanNamePattern(spanNamePattern));
                }
            }
        }

        this.spanNamePatterns = patternNamePairs;
        this.defaultSpanName = defaultSpanName;
    }

    @Override
    public String filterSpanName(String unfilteredSpanName) {
        for (SpanNamePattern spanNamePattern : spanNamePatterns) {
            if (spanNamePattern.matches(unfilteredSpanName)) {
                return spanNamePattern.getName();
            }
        }
        return defaultSpanName;
    }

    private static class SpanNamePattern {
        private final String name;
        private final Pattern pattern;

        private SpanNamePattern(String name) {
            this.name = name;
            this.pattern = Pattern.compile(name.replaceAll("\\{.+?\\}", ".+?"), Pattern.CASE_INSENSITIVE);
        }

        public String getName() {
            return name;
        }

        public boolean matches(String input) {
            return pattern.matcher(input).matches();
        }
    }
}

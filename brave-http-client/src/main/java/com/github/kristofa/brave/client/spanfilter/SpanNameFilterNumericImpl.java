package com.github.kristofa.brave.client.spanfilter;

/**
 * Implementation of {@link SpanNameFilter} that will filter out path parths with numeric values.
 *
 * @author Pieter Cailliau (K-Jo)
 */
public class SpanNameFilterNumericImpl implements SpanNameFilter {

    /**
     * Will replace numeric path parths with < numeric >
     * <p>
     * {@inheritDoc}
     */
    @Override
    public String filterSpanName(final String unfilteredSpanName) {
        final String[] split = unfilteredSpanName.split("/");
        final StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < split.length; i++) {
            if (split[i].matches(".*\\d.*")) {
                stringBuilder.append("<numeric>");
            } else {
                stringBuilder.append(split[i]);
            }
            if (i < split.length - 1) {
                stringBuilder.append("/");
            }
        }
        return stringBuilder.toString();
    }
}
/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave;

/**
 * Simple interface users can customize a span with. For example, this can add custom tags useful in
 * looking up spans.
 *
 * <h3>Usage notes</h3>
 * This type is safer to expose directly to users than {@link Span}, as it has no hooks that can
 * affect the span lifecycle.
 *
 * @see Tag
 */
// Java language level 6. Do not add methods as it will break API!
public interface SpanCustomizer {
  /**
   * Sets the string name for the logical operation this span represents.
   */
  SpanCustomizer name(String name);

  /**
   * Tags give your span context for search, viewing and analysis. For example, a key
   * "your_app.version" would let you lookup spans by version. A tag "sql.query" isn't searchable,
   * but it can help in debugging when viewing a trace.
   *
   * <p><em>Note:</em>To guard potentially expensive parsing, implement {@link Tag} instead, which
   * avoids parsing into a no-op span.
   *
   * <p>Ex.
   * <pre>{@code
   * SUMMARY_TAG = new Tag<Summarizer>("summary") {
   *   @Override protected String parseValue(Summarizer input, TraceContext context) {
   *     return input.computeSummary();
   *   }
   * }
   * SUMMARY_TAG.tag(span);
   * }</pre>
   *
   * @param key Name used to lookup spans, such as "your_app.version".
   * @param value String value, cannot be <code>null</code>.
   * @see Tag#tag(Object, SpanCustomizer)
   */
  SpanCustomizer tag(String key, String value);

  /**
   * Associates an event that explains latency with the current system time.
   *
   * @param value A short tag indicating the event, like "finagle.retry"
   */
  SpanCustomizer annotate(String value);
}

package brave;

import zipkin.Constants;
import zipkin.TraceKeys;

/**
 * Simple interface users can customize a span with. For example, this can add custom tags useful
 * in looking up spans.
 *
 * <p>This type is safer to expose directly to users than {@link Span}, as it has no hooks that
 * can affect the span lifecycle.
 */
// Note: this is exposed to users. We cannot add methods to this until Java 8 is required or we do a
// major version bump
public interface SpanCustomizer {
  /**
   * Sets the string name for the logical operation this span represents.
   */
  SpanCustomizer name(String name);

  /**
   * Tags give your span context for search, viewing and analysis. For example, a key
   * "your_app.version" would let you lookup spans by version. A tag {@link TraceKeys#SQL_QUERY}
   * isn't searchable, but it can help in debugging when viewing a trace.
   *
   * @param key Name used to lookup spans, such as "your_app.version". See {@link TraceKeys} for
   * standard ones.
   * @param value String value, cannot be <code>null</code>.
   */
  SpanCustomizer tag(String key, String value);

  /**
   * Associates an event that explains latency with the current system time.
   *
   * @param value A short tag indicating the event, like "finagle.retry"
   * @see Constants
   */
  SpanCustomizer annotate(String value);

  /** Like {@link #annotate(String)}, except with a given timestamp in microseconds. */
  SpanCustomizer annotate(long timestamp, String value);
}

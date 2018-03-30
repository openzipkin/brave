package brave;

import zipkin.Constants;
import zipkin.TraceKeys;

/**
 * Simple interface users can customize a span with. For example, this can add custom tags useful
 * in looking up spans.
 *
 * <p>This type is safer to expose directly to users than {@link Span}, as it has no hooks that
 * can affect the span lifecycle.
 *
 * <p>While unnecessary when tagging constants, guard potentially expensive operations on the
 * {@link NoopSpanCustomizer} type.
 *
 * <p>Ex.
 * <pre>{@code
 * if (!(customizer instanceof NoopSpanCustomizer)) {
 *   customizer.tag("summary", computeSummary());
 * }
 * }</pre>
 */
// Note: this is exposed to users. We cannot add methods to this until Java 8 is required or we do a
// major version bump
// BRAVE5: add isNoop to avoid instanceof checks
// BRAVE5: add error to support error handling
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

  /** @deprecated use {@link #annotate(String)} as this can result in clock skew */
  // BRAVE5: remove this: backdating ops should only be available on Span, as it isn't reasonable
  // for those only having a reference to SpanCustomizer to have a correct clock for the trace.
  @Deprecated SpanCustomizer annotate(long timestamp, String value);
}

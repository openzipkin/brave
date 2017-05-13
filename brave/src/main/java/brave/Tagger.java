package brave;

import zipkin.TraceKeys;

/**
 * Simple interface users can customize a span with. For example, this can add custom tags useful
 * in looking up spans.
 *
 * <p>This interface is safer to expose directly to users than {@link Span}, as it has no hooks that
 * can affect the span lifecycle.
 */
public interface Tagger {

  /**
   * Tags give your span context for search, viewing and analysis. For example, a key
   * "your_app.version" would let you lookup spans by version. A tag {@link TraceKeys#SQL_QUERY}
   * isn't searchable, but it can help in debugging when viewing a trace.
   *
   * @param key Name used to lookup spans, such as "your_app.version". See {@link TraceKeys} for
   * standard ones.
   * @param value String value, cannot be <code>null</code>.
   */
  Tagger tag(String key, String value);
}

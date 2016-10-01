package com.github.kristofa.brave;

import java.util.logging.Level;
import java.util.logging.Logger;
import zipkin.reporter.Reporter;

import static com.github.kristofa.brave.internal.Util.checkNotBlank;
import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Simple {@link Reporter} implementation which logs the span through jul at INFO level.
 *
 * <p>Can be used for testing and debugging.
 */
public final class LoggingReporter implements Reporter<zipkin.Span> {

  final Logger logger;

  /**
   * Note: this logs to the category "com.github.kristofa.brave.LoggingSpanCollector" for backwards
   * compatiblity purposes.
   */
  public LoggingReporter() {
    this("com.github.kristofa.brave.LoggingSpanCollector");
  }

  public LoggingReporter(String loggerName) {
    checkNotBlank(loggerName, "Null or blank loggerName");
    logger = Logger.getLogger(loggerName);
  }

  @Override public void report(zipkin.Span span) {
    checkNotNull(span, "Null span");
    if (logger.isLoggable(Level.INFO)) {
      logger.info(span.toString());
    }
  }
}

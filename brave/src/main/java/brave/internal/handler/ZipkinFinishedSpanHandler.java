/*
 * Copyright 2013-2020 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.internal.handler;

import brave.Tracer;
import brave.handler.FinishedSpanHandler;
import brave.handler.MutableSpan;
import brave.internal.Nullable;
import brave.propagation.TraceContext;
import java.util.logging.Level;
import java.util.logging.Logger;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

/**
 * Logs exceptions instead of raising an error, as the supplied reporter could have bugs.
 *
 * <p><em>Note:</em> This is an internal type and will change at any time.
 */
public final class ZipkinFinishedSpanHandler extends FinishedSpanHandler {
  final Reporter<Span> spanReporter;
  final MutableSpanConverter converter;
  final boolean alwaysReportSpans;

  public ZipkinFinishedSpanHandler(MutableSpan defaultSpan, @Nullable Reporter<Span> spanReporter,
    boolean alwaysReportSpans) {
    this.spanReporter = spanReporter != null ? spanReporter : new LoggingReporter();
    this.converter = new MutableSpanConverter(defaultSpan);
    this.alwaysReportSpans = alwaysReportSpans;
  }

  public static final class LoggingReporter implements Reporter<Span> {
    final Logger logger = Logger.getLogger(Tracer.class.getName());

    @Override public void report(Span span) {
      if (span == null) throw new NullPointerException("span == null");
      if (!logger.isLoggable(Level.INFO)) return;
      logger.info(span.toString());
    }

    @Override public String toString() {
      return "LoggingReporter{name=" + logger.getName() + "}";
    }
  }

  /**
   * This is the last in the chain of finished span handlers. A predecessor may have set {@link
   * #alwaysSampleLocal()}, so we have to double-check here that the span was sampled to Zipkin.
   * Otherwise, we could accidentally send 100% data.
   */
  @Override public boolean handle(TraceContext context, MutableSpan span) {
    if (!alwaysReportSpans && !Boolean.TRUE.equals(context.sampled())) return true;

    Span.Builder builderWithContextData = Span.newBuilder()
      .traceId(context.traceIdString())
      .parentId(context.parentIdString())
      .id(context.spanIdString());

    converter.convert(span, builderWithContextData);
    spanReporter.report(builderWithContextData.build());
    return true;
  }

  @Override public boolean supportsOrphans() {
    return true;
  }

  @Override public String toString() {
    return spanReporter.toString();
  }
}

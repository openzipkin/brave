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

import brave.ErrorParser;
import brave.Tags;
import brave.handler.FinishedSpanHandler;
import brave.handler.MutableSpan;
import brave.internal.Nullable;
import brave.propagation.TraceContext;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

/**
 * Logs exceptions instead of raising an error, as the supplied reporter could have bugs.
 *
 * <p><em>Note:</em> This is an internal type and will change at any time.
 */
public final class ZipkinFinishedSpanHandler extends FinishedSpanHandler {
  final Reporter<Span> spanReporter;
  /**
   * Zipkin format uses the {@linkplain Tags#ERROR "error" tag}, but alternative formats may have a
   * different tag name or a field entirely. Hence, we only create the "error" tag here, and only if
   * not previously set.
   */
  final ErrorParser errorParser;

  final String defaultServiceName, defaultIp;
  final int defaultPort;
  final Endpoint defaultEndpoint;
  final boolean alwaysReportSpans;

  public ZipkinFinishedSpanHandler(MutableSpan defaultSpan, Reporter<Span> spanReporter,
    ErrorParser errorParser, boolean alwaysReportSpans) {
    this.spanReporter = spanReporter;
    this.errorParser = errorParser;
    this.defaultServiceName = defaultSpan.localServiceName();
    this.defaultPort = defaultSpan.localPort();
    this.defaultIp = defaultSpan.localIp();
    this.defaultEndpoint = Endpoint.newBuilder()
      .serviceName(defaultServiceName)
      .ip(defaultIp)
      .port(defaultPort)
      .build();
    this.alwaysReportSpans = alwaysReportSpans;
  }

  /**
   * This is the last in the chain of finished span handlers. A predecessor may have set {@link
   * #alwaysSampleLocal()}, so we have to double-check here that the span was sampled to Zipkin.
   * Otherwise, we could accidentally send 100% data.
   */
  @Override public boolean handle(TraceContext context, MutableSpan span) {
    if (!alwaysReportSpans && !Boolean.TRUE.equals(context.sampled())) return true;
    spanReporter.report(convert(span));
    return true;
  }

  @Override public boolean supportsOrphans() {
    return true;
  }

  Span convert(MutableSpan span) {
    Span.Builder result = Span.newBuilder()
      .traceId(span.traceId())
      .parentId(span.parentId())
      .id(span.id())
      .name(span.name());

    long start = span.startTimestamp(), finish = span.finishTimestamp();
    result.timestamp(start);
    if (start != 0 && finish != 0L) result.duration(Math.max(finish - start, 1));

    // use ordinal comparison to defend against version skew
    brave.Span.Kind kind = span.kind();
    if (kind != null && kind.ordinal() < Span.Kind.values().length) {
      result.kind(Span.Kind.values()[kind.ordinal()]);
    }

    String localServiceName = span.localServiceName(), localIp = span.localIp();
    if (localServiceName != null || localIp != null) {
      addLocalEndpoint(localServiceName, localIp, span.localPort(), result);
    }

    String remoteServiceName = span.remoteServiceName(), remoteIp = span.remoteIp();
    if (remoteServiceName != null || remoteIp != null) {
      result.remoteEndpoint(zipkin2.Endpoint.newBuilder()
        .serviceName(remoteServiceName)
        .ip(remoteIp)
        .port(span.remotePort())
        .build());
    }

    maybeAddErrorTag(span);
    span.forEachTag(Consumer.INSTANCE, result);
    span.forEachAnnotation(Consumer.INSTANCE, result);
    if (span.shared()) result.shared(true);
    if (span.debug()) result.debug(true);
    return result.build();
  }

  // avoid re-allocating an endpoint when we have the same data
  void addLocalEndpoint(@Nullable String serviceName, @Nullable String ip, int port,
    Span.Builder span) {
    // We are not comparing against the potentially lower-cased endpoint, rather values passed to
    // Tracing.Builder. Hence, we do not need the overhead of mixed-case comparison here.
    if (equal(serviceName, defaultServiceName) && equal(ip, defaultIp) && port == defaultPort) {
      span.localEndpoint(defaultEndpoint);
    } else {
      span.localEndpoint(Endpoint.newBuilder().serviceName(serviceName).ip(ip).port(port).build());
    }
  }

  void maybeAddErrorTag(MutableSpan span) {
    if (span.error() == null) return; // span.tag(key) iterates: check if we need to first!
    String errorTag = span.tag("error");
    if (errorTag == null) errorParser.error(span.error(), span);
  }

  @Override public String toString() {
    return spanReporter.toString();
  }

  enum Consumer implements MutableSpan.TagConsumer<Span.Builder>,
    MutableSpan.AnnotationConsumer<Span.Builder> {
    INSTANCE;

    @Override public void accept(Span.Builder target, String key, String value) {
      target.putTag(key, value);
    }

    @Override public void accept(Span.Builder target, long timestamp, String value) {
      target.addAnnotation(timestamp, value);
    }
  }

  static boolean equal(@Nullable Object a, @Nullable Object b) {
    return a == null ? b == null : a.equals(b); // Java 6 can't use Objects.equals()
  }
}

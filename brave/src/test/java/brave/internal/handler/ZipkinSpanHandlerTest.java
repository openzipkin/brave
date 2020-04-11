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
import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import zipkin2.Annotation;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

import static brave.Span.Kind.CLIENT;
import static brave.Span.Kind.SERVER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class ZipkinSpanHandlerTest {
  List<Span> spans = new ArrayList<>();
  ZipkinSpanHandler handler;
  MutableSpan defaultSpan;

  @Before public void init() {
    defaultSpan = spanWithIds();
    defaultSpan.localServiceName("Aa");
    defaultSpan.localIp("1.2.3.4");
    defaultSpan.localPort(80);
    init(spans::add, false);
  }

  void init(Reporter<Span> spanReporter, boolean alwaysReportSpans) {
    MutableSpan defaultSpan = new MutableSpan();
    defaultSpan.localServiceName("favistar");
    defaultSpan.localIp("1.2.3.4");
    handler =
      new ZipkinSpanHandler(defaultSpan, spanReporter, ErrorParser.get(), alwaysReportSpans);
  }

  @Test public void reportsSampledSpan() {
    TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true).build();
    MutableSpan span = new MutableSpan();
    span.traceId(context.traceIdString());
    span.id(context.spanIdString());
    handler.end(context, span, SpanHandler.Cause.FINISH);

    assertThat(spans.get(0)).isEqualToComparingFieldByField(
      Span.newBuilder()
        .traceId("1")
        .id("2")
        .build()
    );
  }

  @Test public void reportsDebugSpan() {
    TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).debug(true).build();
    MutableSpan span = new MutableSpan();
    span.traceId(context.traceIdString());
    span.id(context.spanIdString());
    span.setDebug();
    handler.end(context, span, SpanHandler.Cause.FINISH);

    assertThat(spans.get(0)).isEqualToComparingFieldByField(
      Span.newBuilder()
        .traceId("1")
        .id("2")
        .debug(true)
        .build()
    );
  }

  @Test public void doesntReportUnsampledSpan() {
    TraceContext context =
      TraceContext.newBuilder().traceId(1).spanId(2).sampled(false).sampledLocal(true).build();
    handler.end(context, new MutableSpan(), SpanHandler.Cause.FINISH);

    assertThat(spans).isEmpty();
  }

  @Test public void alwaysReportSpans_reportUnsampledSpan() {
    init(spans::add, true);

    TraceContext context =
      TraceContext.newBuilder().traceId(1).spanId(2).sampled(false).sampledLocal(true).build();
    MutableSpan span = new MutableSpan();
    span.traceId(context.traceIdString());
    span.id(context.spanIdString());
    handler.end(context, span, SpanHandler.Cause.FINISH);

    assertThat(spans).isNotEmpty();
  }

  @Test public void localEndpoint_null() {
    // When a finished span handler clears the endpoint info
    assertThat(handler.convert(spanWithIds()).localEndpoint())
      .isNull();
  }

  @Test public void localEndpoint_sameAsDefault() {
    // When span sets to the same values
    MutableSpan span = spanWithIds();
    span.localServiceName(handler.defaultEndpoint.serviceName());
    span.localIp(handler.defaultEndpoint.ipv4());
    span.localPort(handler.defaultEndpoint.portAsInt());

    assertThat(handler.convert(span).localEndpoint())
      .isSameAs(handler.defaultEndpoint);
  }

  @Test public void localEndpoint_serviceNameHashCodeCollision() {
    MutableSpan span = spanWithIds();
    span.localServiceName("BB");
    span.localIp(handler.defaultEndpoint.ipv4());
    span.localPort(handler.defaultEndpoint.portAsInt());

    assertThat(handler.convert(span).localEndpoint())
      .isNotSameAs(handler.defaultEndpoint);
  }

  @Test public void localEndpoint_notSameAsDefault_missingIp() {
    MutableSpan span = spanWithIds();
    span.localServiceName(handler.defaultEndpoint.serviceName());
    // missing IP address
    span.localPort(handler.defaultEndpoint.portAsInt());

    assertThat(handler.convert(span).localEndpoint().ipv4())
      .isNull();
  }

  @Test public void localEndpoint_notSameAsDefault_differentIp() {
    MutableSpan span = spanWithIds();
    span.localServiceName(handler.defaultEndpoint.serviceName());
    span.localIp("2001:db8::c001");
    span.localPort(handler.defaultEndpoint.portAsInt());

    assertThat(handler.convert(span).localEndpoint().ipv6())
      .isEqualTo("2001:db8::c001");
  }

  @Test public void localEndpoint_notSameAsDefault_missingPort() {
    MutableSpan span = spanWithIds();
    span.localServiceName(handler.defaultEndpoint.serviceName());
    span.localIp(handler.defaultEndpoint.ipv4());
    // missing port

    assertThat(handler.convert(span).localEndpoint().portAsInt())
      .isZero();
  }

  @Test public void localEndpoint_notSameAsDefault_differentPort() {
    MutableSpan span = spanWithIds();
    span.localServiceName(handler.defaultEndpoint.serviceName());
    span.localIp(handler.defaultEndpoint.ipv4());
    span.localPort(443);

    assertThat(handler.convert(span).localEndpoint().portAsInt())
      .isEqualTo(443);
  }

  @Test public void localEndpoint_override() {
    MutableSpan span = spanWithIds();
    span.localServiceName("barService");

    assertThat(handler.convert(span).localEndpoint())
      .isEqualTo(Endpoint.newBuilder().serviceName("barService").build());
  }

  @Test public void minimumDurationIsOne() {
    MutableSpan span = spanWithIds();

    span.startTimestamp(1L);
    span.finishTimestamp(1L);

    assertThat(handler.convert(span).duration()).isEqualTo(1L);
  }

  @Test public void replacesTag() {
    MutableSpan span = spanWithIds();

    span.tag("1", "1");
    span.tag("foo", "bar");
    span.tag("2", "2");
    span.tag("foo", "baz");
    span.tag("3", "3");

    assertThat(handler.convert(span).tags()).containsOnly(
      entry("1", "1"),
      entry("foo", "baz"),
      entry("2", "2"),
      entry("3", "3")
    );
  }

  Throwable ERROR = new RuntimeException();

  @Test public void backfillsErrorTag() {
    MutableSpan span = spanWithIds();

    span.error(ERROR);

    assertThat(handler.convert(span).tags())
      .containsOnly(entry("error", "RuntimeException"));
  }

  @Test public void doesntOverwriteErrorTag() {
    MutableSpan span = spanWithIds();

    span.error(ERROR);
    span.tag("error", "");

    assertThat(handler.convert(span).tags())
      .containsOnly(entry("error", ""));
  }

  @Test public void addsAnnotations() {
    MutableSpan span = spanWithIds();

    span.startTimestamp(1L);
    span.annotate(2L, "foo");
    span.finishTimestamp(2L);

    assertThat(handler.convert(span).annotations())
      .containsOnly(Annotation.create(2L, "foo"));
  }

  @Test public void finished_client() {
    finish(brave.Span.Kind.CLIENT, Span.Kind.CLIENT);
  }

  @Test public void finished_server() {
    finish(brave.Span.Kind.SERVER, Span.Kind.SERVER);
  }

  @Test public void finished_producer() {
    finish(brave.Span.Kind.PRODUCER, Span.Kind.PRODUCER);
  }

  @Test public void finished_consumer() {
    finish(brave.Span.Kind.CONSUMER, Span.Kind.CONSUMER);
  }

  void finish(brave.Span.Kind braveKind, Span.Kind span2Kind) {
    MutableSpan span = spanWithIds();
    span.kind(braveKind);
    span.startTimestamp(1L);
    span.finishTimestamp(2L);

    Span span2 = handler.convert(span);
    assertThat(span2.annotations()).isEmpty();
    assertThat(span2.timestamp()).isEqualTo(1L);
    assertThat(span2.duration()).isEqualTo(1L);
    assertThat(span2.kind()).isEqualTo(span2Kind);
  }

  @Test public void flushed_client() {
    flush(brave.Span.Kind.CLIENT, Span.Kind.CLIENT);
  }

  @Test public void flushed_server() {
    flush(brave.Span.Kind.SERVER, Span.Kind.SERVER);
  }

  @Test public void flushed_producer() {
    flush(brave.Span.Kind.PRODUCER, Span.Kind.PRODUCER);
  }

  @Test public void flushed_consumer() {
    flush(brave.Span.Kind.CONSUMER, Span.Kind.CONSUMER);
  }

  void flush(brave.Span.Kind braveKind, Span.Kind span2Kind) {
    MutableSpan span = spanWithIds();
    span.kind(braveKind);
    span.startTimestamp(1L);
    span.finishTimestamp(0L);

    Span span2 = handler.convert(span);
    assertThat(span2.annotations()).isEmpty();
    assertThat(span2.timestamp()).isEqualTo(1L);
    assertThat(span2.duration()).isNull();
    assertThat(span2.kind()).isEqualTo(span2Kind);
  }

  @Test public void remoteEndpoint() {
    MutableSpan span = spanWithIds();

    Endpoint endpoint = Endpoint.newBuilder()
      .serviceName("fooService")
      .ip("1.2.3.4")
      .port(80)
      .build();

    span.kind(CLIENT);
    span.remoteServiceName(endpoint.serviceName());
    span.remoteIpAndPort(endpoint.ipv4(), endpoint.port());
    span.startTimestamp(1L);
    span.finishTimestamp(2L);

    assertThat(handler.convert(span).remoteEndpoint())
      .isEqualTo(endpoint);
  }

  // This prevents the server startTimestamp from overwriting the client one on the collector
  @Test public void writeTo_sharedStatus() {
    MutableSpan span = spanWithIds();

    span.setShared();
    span.startTimestamp(1L);
    span.kind(SERVER);
    span.finishTimestamp(2L);

    assertThat(handler.convert(span).shared())
      .isTrue();
  }

  @Test public void flushUnstartedNeitherSetsTimestampNorDuration() {
    MutableSpan flushed = spanWithIds();
    flushed.finishTimestamp(0L);

    assertThat(handler.convert(flushed)).extracting(Span::timestampAsLong, Span::durationAsLong)
      .allSatisfy(u -> assertThat(u).isEqualTo(0L));
  }

  static MutableSpan spanWithIds() {
    MutableSpan result = new MutableSpan();
    result.traceId("a");
    result.id("b");
    return result;
  }
}

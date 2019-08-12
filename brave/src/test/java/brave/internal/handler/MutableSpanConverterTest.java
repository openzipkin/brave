/*
 * Copyright 2013-2019 The OpenZipkin Authors
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
import brave.Span.Kind;
import brave.handler.MutableSpan;
import org.junit.Test;
import zipkin2.Annotation;
import zipkin2.Endpoint;
import zipkin2.Span;

import static brave.Span.Kind.CLIENT;
import static brave.Span.Kind.SERVER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class MutableSpanConverterTest {
  MutableSpanConverter converter =
    new MutableSpanConverter(new ErrorParser(), "fooService", "1.2.3.4", 80);

  @Test public void localEndpoint_default() {
    // When span doesn't set local endpoint info
    assertThat(convert(new MutableSpan()).localEndpoint())
      .isEqualTo(converter.localEndpoint);

    // When span sets to the same values
    MutableSpan span = new MutableSpan();
    span.localServiceName(converter.localServiceName);
    span.localIp(converter.localIp);
    span.localPort(converter.localPort);

    assertThat(convert(span).localEndpoint())
      .isEqualTo(converter.localEndpoint);
  }

  @Test public void localEndpoint_default_whenIpNull() {
    converter = new MutableSpanConverter(new ErrorParser(), "fooService", null, 80);

    // When span doesn't set local endpoint info
    assertThat(convert(new MutableSpan()).localEndpoint())
      .isEqualTo(converter.localEndpoint);

    // When span sets to the same values
    MutableSpan span = new MutableSpan();
    span.localServiceName(converter.localServiceName);
    span.localPort(converter.localPort);

    assertThat(convert(span).localEndpoint())
      .isEqualTo(converter.localEndpoint);
  }

  @Test public void localEndpoint_override() {
    MutableSpan span = new MutableSpan();
    span.localServiceName("barService");

    assertThat(convert(span).localEndpoint())
      .isEqualTo(Endpoint.newBuilder().serviceName("barService").ip("1.2.3.4").port(80).build());
  }

  @Test public void minimumDurationIsOne() {
    MutableSpan span = new MutableSpan();

    span.startTimestamp(1L);
    span.finishTimestamp(1L);

    assertThat(convert(span).duration()).isEqualTo(1L);
  }

  @Test public void replacesTag() {
    MutableSpan span = new MutableSpan();

    span.tag("1", "1");
    span.tag("foo", "bar");
    span.tag("2", "2");
    span.tag("foo", "baz");
    span.tag("3", "3");

    assertThat(convert(span).tags()).containsOnly(
      entry("1", "1"),
      entry("foo", "baz"),
      entry("2", "2"),
      entry("3", "3")
    );
  }

  @Test public void addsAnnotations() {
    MutableSpan span = new MutableSpan();

    span.startTimestamp(1L);
    span.annotate(2L, "foo");
    span.finishTimestamp(2L);

    assertThat(convert(span).annotations())
      .containsOnly(Annotation.create(2L, "foo"));
  }

  @Test public void finished_client() {
    finish(Kind.CLIENT, Span.Kind.CLIENT);
  }

  @Test public void finished_server() {
    finish(Kind.SERVER, Span.Kind.SERVER);
  }

  @Test public void finished_producer() {
    finish(Kind.PRODUCER, Span.Kind.PRODUCER);
  }

  @Test public void finished_consumer() {
    finish(Kind.CONSUMER, Span.Kind.CONSUMER);
  }

  void finish(Kind braveKind, Span.Kind span2Kind) {
    MutableSpan span = new MutableSpan();
    span.kind(braveKind);
    span.startTimestamp(1L);
    span.finishTimestamp(2L);

    Span span2 = convert(span);
    assertThat(span2.annotations()).isEmpty();
    assertThat(span2.timestamp()).isEqualTo(1L);
    assertThat(span2.duration()).isEqualTo(1L);
    assertThat(span2.kind()).isEqualTo(span2Kind);
  }

  @Test public void flushed_client() {
    flush(Kind.CLIENT, Span.Kind.CLIENT);
  }

  @Test public void flushed_server() {
    flush(Kind.SERVER, Span.Kind.SERVER);
  }

  @Test public void flushed_producer() {
    flush(Kind.PRODUCER, Span.Kind.PRODUCER);
  }

  @Test public void flushed_consumer() {
    flush(Kind.CONSUMER, Span.Kind.CONSUMER);
  }

  void flush(Kind braveKind, Span.Kind span2Kind) {
    MutableSpan span = new MutableSpan();
    span.kind(braveKind);
    span.startTimestamp(1L);
    span.finishTimestamp(0L);

    Span span2 = convert(span);
    assertThat(span2.annotations()).isEmpty();
    assertThat(span2.timestamp()).isEqualTo(1L);
    assertThat(span2.duration()).isNull();
    assertThat(span2.kind()).isEqualTo(span2Kind);
  }

  @Test public void remoteEndpoint() {
    MutableSpan span = new MutableSpan();

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

    assertThat(convert(span).remoteEndpoint())
      .isEqualTo(endpoint);
  }

  // This prevents the server startTimestamp from overwriting the client one on the collector
  @Test public void writeTo_sharedStatus() {
    MutableSpan span = new MutableSpan();

    span.setShared();
    span.startTimestamp(1L);
    span.kind(SERVER);
    span.finishTimestamp(2L);

    assertThat(convert(span).shared())
      .isTrue();
  }

  @Test public void flushUnstartedNeitherSetsTimestampNorDuration() {
    MutableSpan flushed = new MutableSpan();
    flushed.finishTimestamp(0L);

    assertThat(convert(flushed)).extracting(s -> s.timestampAsLong(), s -> s.durationAsLong())
      .allSatisfy(u -> assertThat(u).isEqualTo(0L));
  }

  /** We can't compute duration unless we started the span in the same tracer. */
  @Test public void writeTo_finishUnstartedIsSameAsFlush() {
    MutableSpan finishWithTimestamp = new MutableSpan();
    finishWithTimestamp.finishTimestamp(2L);
    Span.Builder finishWithTimestampBuilder = Span.newBuilder();
    converter.convert(finishWithTimestamp, finishWithTimestampBuilder);

    MutableSpan finishWithNoTimestamp = new MutableSpan();
    finishWithNoTimestamp.finishTimestamp(0L);
    Span.Builder finishWithNoTimestampBuilder = Span.newBuilder();
    converter.convert(finishWithNoTimestamp, finishWithNoTimestampBuilder);

    MutableSpan flush = new MutableSpan();
    Span.Builder flushBuilder = Span.newBuilder();
    converter.convert(flush, flushBuilder);

    assertThat(finishWithTimestampBuilder)
      .usingRecursiveComparison()
      .isEqualTo(finishWithNoTimestampBuilder)
      .isEqualTo(flushBuilder);
  }

  Span convert(MutableSpan span) {
    Span.Builder result = Span.newBuilder().traceId(0L, 1L).id(1L);
    converter.convert(span, result);
    return result.build();
  }
}

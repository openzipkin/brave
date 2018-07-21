package brave.internal.recorder;

import brave.Span.Kind;
import brave.internal.Platform;
import brave.propagation.TraceContext;
import org.junit.Test;
import zipkin2.Annotation;
import zipkin2.Endpoint;
import zipkin2.Span;

import static brave.Span.Kind.CLIENT;
import static brave.Span.Kind.SERVER;
import static org.assertj.core.api.Assertions.assertThat;

public class MutableSpanTest {
  Endpoint localEndpoint = Platform.get().endpoint();
  TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).build();

  @Test public void minimumDurationIsOne() {
    MutableSpan span = newSpan();

    span.start(1L);
    span.finish(1L);

    assertThat(toZipkinSpan(span).duration()).isEqualTo(1L);
  }

  @Test public void addsAnnotations() {
    MutableSpan span = newSpan();

    span.start(1L);
    span.annotate(2L, "foo");
    span.finish(2L);

    assertThat(toZipkinSpan(span).annotations())
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

  private void finish(Kind braveKind, Span.Kind span2Kind) {
    MutableSpan span = newSpan();
    span.kind(braveKind);
    span.start(1L);
    span.finish(2L);

    Span span2 = toZipkinSpan(span);
    assertThat(span2.annotations()).isEmpty();
    assertThat(span2.timestamp()).isEqualTo(1L);
    assertThat(span2.duration()).isEqualTo(1L);
    assertThat(span2.kind()).isEqualTo(span2Kind);
  }

  @Test
  public void finished_client_annotation() {
    finish("cs", "cr", Span.Kind.CLIENT);
  }

  @Test
  public void finished_server_annotation() {
    finish("sr", "ss", Span.Kind.SERVER);
  }

  private void finish(String start, String end, Span.Kind span2Kind) {
    MutableSpan span = newSpan();
    span.annotate(1L, start);
    span.annotate(2L, end);

    Span span2 = toZipkinSpan(span);
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

  private void flush(Kind braveKind, Span.Kind span2Kind) {
    MutableSpan span = newSpan();
    span.kind(braveKind);
    span.start(1L);
    span.finish(0L);

    Span span2 = toZipkinSpan(span);
    assertThat(span2.annotations()).isEmpty();
    assertThat(span2.timestamp()).isEqualTo(1L);
    assertThat(span2.duration()).isNull();
    assertThat(span2.kind()).isEqualTo(span2Kind);
  }

  @Test public void remoteEndpoint() {
    MutableSpan span = newSpan();

    Endpoint endpoint = Endpoint.newBuilder().serviceName("server").build();
    span.kind(CLIENT);
    span.remoteEndpoint(endpoint);
    span.start(1L);
    span.finish(2L);

    assertThat(toZipkinSpan(span).remoteEndpoint())
        .isEqualTo(endpoint);
  }

  // This prevents the server timestamp from overwriting the client one on the collector
  @Test public void reportsSharedStatus() {
    MutableSpan span = new MutableSpan(() -> 0L, context);

    span.setShared();
    span.start(1L);
    span.kind(SERVER);
    span.finish(2L);

    assertThat(toZipkinSpan(span).shared())
        .isTrue();
  }

  @Test public void flushUnstartedNeitherSetsTimestampNorDuration() {
    MutableSpan flushed = newSpan();
    flushed.finish(0L);
    assertThat(flushed).extracting(s -> s.timestamp, s -> toZipkinSpan(s).durationAsLong())
        .allSatisfy(u -> assertThat(u).isEqualTo(0L));
  }

  /** We can't compute duration unless we started the span in the same tracer. */
  @Test public void finishUnstartedIsSameAsFlush() {
    MutableSpan finishWithTimestamp = newSpan();
    finishWithTimestamp.finish(2L);

    MutableSpan finishWithNoTimestamp = newSpan();
    finishWithNoTimestamp.finish(0L);

    MutableSpan flush = newSpan();

    assertThat(finishWithTimestamp)
        .isEqualToComparingFieldByFieldRecursively(finishWithNoTimestamp)
        .isEqualToComparingFieldByFieldRecursively(flush);
  }

  MutableSpan newSpan() {
    return new MutableSpan(() -> 0L, context);
  }

  Span toZipkinSpan(MutableSpan span) {
    return span.toSpan(localEndpoint);
  }
}

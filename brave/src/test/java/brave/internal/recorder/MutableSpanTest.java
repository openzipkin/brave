package brave.internal.recorder;

import brave.Span.Kind;
import brave.Tracing;
import brave.internal.Platform;
import brave.propagation.TraceContext;
import org.junit.After;
import org.junit.Test;
import zipkin2.Annotation;
import zipkin2.Endpoint;
import zipkin2.Span;

import static brave.Span.Kind.CLIENT;
import static brave.Span.Kind.SERVER;
import static org.assertj.core.api.Assertions.assertThat;

public class MutableSpanTest {
  Endpoint localEndpoint = Platform.get().endpoint();
  TraceContext context = Tracing.newBuilder().build().tracer().newTrace().context();

  @After public void close() {
    Tracing.current().close();
  }

  // zipkin needs one annotation or binary annotation so that the local endpoint can be read
  @Test public void addsLocalEndpoint() {
    MutableSpan span = newSpan();

    span.start(1L);
    span.finish(2L);

    assertThat(span.toSpan().localEndpoint())
        .isEqualTo(localEndpoint);
  }

  @Test public void minimumDurationIsOne() {
    MutableSpan span = newSpan();

    span.start(1L).finish(1L);

    assertThat(span.toSpan().duration()).isEqualTo(1L);
  }

  @Test public void addsAnnotations() {
    MutableSpan span = newSpan();

    span.start(1L);
    span.annotate(2L, "foo");
    span.finish(2L);

    assertThat(span.toSpan().annotations())
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

    Span span2 = span.toSpan();
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

    Span span2 = span.toSpan();
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
    span.finish(null);

    Span span2 = span.toSpan();
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

    assertThat(span.toSpan().remoteEndpoint())
        .isEqualTo(endpoint);
  }

  // This prevents the server timestamp from overwriting the client one on the collector
  @Test public void reportsSharedStatus() {
    MutableSpan span = new MutableSpan(() -> 0L, context.toBuilder().build(), localEndpoint);

    span.setShared();
    span.start(1L);
    span.kind(SERVER);
    span.finish(2L);

    assertThat(span.toSpan().shared())
        .isTrue();
  }

  @Test public void flushUnstartedNeitherSetsTimestampNorDuration() {
    Span flushed = newSpan().finish(null).toSpan();
    assertThat(flushed).extracting(Span::timestamp, Span::duration)
        .allSatisfy(u -> assertThat(u).isNull());
  }

  /** We can't compute duration unless we started the span in the same tracer. */
  @Test public void finishUnstartedIsSameAsFlush() {
    assertThat(newSpan().finish(2L).toSpan())
        .isEqualTo(newSpan().finish(null).toSpan());
  }

  MutableSpan newSpan() {
    return new MutableSpan(() -> 0L, context, localEndpoint);
  }
}

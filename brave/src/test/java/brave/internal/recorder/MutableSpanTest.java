package brave.internal.recorder;

import brave.Span;
import brave.Tracing;
import brave.internal.Platform;
import brave.propagation.TraceContext;
import org.junit.After;
import org.junit.Test;
import zipkin.Annotation;
import zipkin.Endpoint;
import zipkin.internal.Span2;
import zipkin.internal.Span2Converter;

import static brave.Span.Kind.CLIENT;
import static brave.Span.Kind.SERVER;
import static org.assertj.core.api.Assertions.assertThat;

public class MutableSpanTest {
  Endpoint localEndpoint = Platform.get().localEndpoint();
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
        .containsOnly(Annotation.create(2L, "foo", null));
  }

  @Test public void finished_client() {
    finish(Span.Kind.CLIENT, Span2.Kind.CLIENT);
  }

  @Test public void finished_server() {
    finish(Span.Kind.SERVER, Span2.Kind.SERVER);
  }

  @Test public void finished_producer() {
    finish(Span.Kind.PRODUCER, Span2.Kind.PRODUCER);
  }

  @Test public void finished_consumer() {
    finish(Span.Kind.CONSUMER, Span2.Kind.CONSUMER);
  }

  private void finish(Span.Kind braveKind, Span2.Kind span2Kind) {
    MutableSpan span = newSpan();
    span.kind(braveKind);
    span.start(1L);
    span.finish(2L);

    Span2 span2 = span.toSpan();
    assertThat(span2.annotations()).isEmpty();
    assertThat(span2.timestamp()).isEqualTo(1L);
    assertThat(span2.duration()).isEqualTo(1L);
    assertThat(span2.kind()).isEqualTo(span2Kind);
  }

  @Test public void flushed_client() {
    flush(Span.Kind.CLIENT, Span2.Kind.CLIENT);
  }

  @Test public void flushed_server() {
    flush(Span.Kind.SERVER, Span2.Kind.SERVER);
  }

  @Test public void flushed_producer() {
    flush(Span.Kind.PRODUCER, Span2.Kind.PRODUCER);
  }

  @Test public void flushed_consumer() {
    flush(Span.Kind.CONSUMER, Span2.Kind.CONSUMER);
  }

  private void flush(Span.Kind braveKind, Span2.Kind span2Kind) {
    MutableSpan span = newSpan();
    span.kind(braveKind);
    span.start(1L);
    span.finish(null);

    Span2 span2 = span.toSpan();
    assertThat(span2.annotations()).isEmpty();
    assertThat(span2.timestamp()).isEqualTo(1L);
    assertThat(span2.duration()).isNull();
    assertThat(span2.kind()).isEqualTo(span2Kind);
  }

  @Test public void remoteEndpoint() {
    MutableSpan span = newSpan();

    Endpoint endpoint = Endpoint.create("server", 127 | 1);
    span.kind(CLIENT);
    span.remoteEndpoint(endpoint);
    span.start(1L);
    span.finish(2L);

    assertThat(span.toSpan().remoteEndpoint()).isEqualTo(endpoint);
  }

  // This prevents the server timestamp from overwriting the client one
  @Test public void doesntReportServerTimestampOnSharedSpans() {
    MutableSpan span = new MutableSpan(context.toBuilder().shared(true).build(), localEndpoint);

    span.start(1L);
    span.kind(SERVER);
    span.finish(2L);

    assertThat(Span2Converter.toSpan(span.toSpan())).extracting(s -> s.timestamp, s -> s.duration)
        .allSatisfy(u -> assertThat(u).isNull());
  }

  @Test public void flushUnstartedNeitherSetsTimestampNorDuration() {
    Span2 flushed = newSpan().finish(null).toSpan();
    assertThat(flushed).extracting(s -> s.timestamp(), s -> s.duration())
        .allSatisfy(u -> assertThat(u).isNull());
  }

  /** We can't compute duration unless we started the span in the same tracer. */
  @Test public void finishUnstartedIsSameAsFlush() {
    assertThat(newSpan().finish(2L).toSpan())
        .isEqualTo(newSpan().finish(null).toSpan());
  }

  MutableSpan newSpan() {
    return new MutableSpan(context, localEndpoint);
  }
}

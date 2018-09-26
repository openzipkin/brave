package brave;

import brave.propagation.ThreadLocalCurrentTraceContext;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import zipkin2.Annotation;
import zipkin2.Endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class RealSpanTest {
  List<zipkin2.Span> spans = new ArrayList();
  Tracing tracing = Tracing.newBuilder()
      .currentTraceContext(ThreadLocalCurrentTraceContext.create())
      .spanReporter(spans::add)
      .build();
  Span span = tracing.tracer().newTrace();

  @After public void close() {
    tracing.close();
  }

  @Test public void isNotNoop() {
    assertThat(span.isNoop()).isFalse();
  }

  @Test public void hasRealContext() {
    assertThat(span.context().spanId()).isNotZero();
  }

  @Test public void hasRealCustomizer() {
    assertThat(span.customizer()).isInstanceOf(RealSpanCustomizer.class);
  }

  @Test public void start() {
    span.start();
    span.flush();

    assertThat(spans).hasSize(1).first()
        .extracting(zipkin2.Span::timestamp)
        .isNotNull();
  }

  @Test public void start_timestamp() {
    span.start(2);
    span.flush();

    assertThat(spans).hasSize(1).first()
        .extracting(zipkin2.Span::timestamp)
        .isEqualTo(2L);
  }

  @Test public void finish() {
    span.start();
    span.finish();

    assertThat(spans).hasSize(1).first()
        .extracting(zipkin2.Span::duration)
        .isNotNull();
  }

  @Test public void finish_timestamp() {
    span.start(2);
    span.finish(5);

    assertThat(spans).hasSize(1).first()
        .extracting(zipkin2.Span::duration)
        .isEqualTo(3L);
  }

  @Test public void abandon() {
    span.start();
    span.abandon();

    assertThat(spans).hasSize(0);
  }

  @Test public void annotate() {
    span.annotate("foo");
    span.flush();

    assertThat(spans).flatExtracting(zipkin2.Span::annotations)
        .extracting(Annotation::value)
        .containsExactly("foo");
  }

  @Test public void remoteEndpoint_nulls() {
    span.remoteEndpoint(Endpoint.newBuilder().build());
    span.flush();

    assertThat(spans.get(0).remoteEndpoint()).isNull();
  }

  @Test public void annotate_timestamp() {
    span.annotate(2, "foo");
    span.flush();

    assertThat(spans).flatExtracting(zipkin2.Span::annotations)
        .containsExactly(Annotation.create(2L, "foo"));
  }

  @Test public void tag() {
    span.tag("foo", "bar");
    span.flush();

    assertThat(spans).flatExtracting(s -> s.tags().entrySet())
        .containsExactly(entry("foo", "bar"));
  }

  @Test public void finished_client_annotation() {
    finish("cs", "cr", zipkin2.Span.Kind.CLIENT);
  }

  @Test public void finished_server_annotation() {
    finish("sr", "ss", zipkin2.Span.Kind.SERVER);
  }

  private void finish(String start, String end, zipkin2.Span.Kind span2Kind) {
    Span span = tracing.tracer().newTrace().name("foo").start();
    span.annotate(1L, start);
    span.annotate(2L, end);

    zipkin2.Span span2 = spans.get(0);
    assertThat(span2.annotations()).isEmpty();
    assertThat(span2.timestamp()).isEqualTo(1L);
    assertThat(span2.duration()).isEqualTo(1L);
    assertThat(span2.kind()).isEqualTo(span2Kind);
  }

  @Test public void doubleFinishDoesntDoubleReport() {
    Span span = tracing.tracer().newTrace().name("foo").start();

    span.finish();
    span.finish();

    assertThat(spans).hasSize(1);
  }

  @Test public void finishAfterAbandonDoesntReport() {
    span.start();
    span.abandon();
    span.finish();

    assertThat(spans).hasSize(0);
  }

  @Test public void abandonAfterFinishDoesNothing() {
    span.start();
    span.finish();
    span.abandon();

    assertThat(spans).hasSize(1);
  }

  @Test public void error() {
    span.error(new RuntimeException("this cake is a lie"));
    span.flush();

    assertThat(spans).flatExtracting(s -> s.tags().entrySet())
        .containsExactly(entry("error", "this cake is a lie"));
  }

  @Test public void error_noMessage() {
    span.error(new RuntimeException());
    span.flush();

    assertThat(spans).flatExtracting(s -> s.tags().entrySet())
        .containsExactly(entry("error", "RuntimeException"));
  }
}

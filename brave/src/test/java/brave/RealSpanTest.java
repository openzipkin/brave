package brave;

import brave.internal.Platform;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.Endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class RealSpanTest {
  List<zipkin.Span> spans = new ArrayList();
  Endpoint localEndpoint = Platform.get().localEndpoint();
  Tracer tracer = Tracing.newBuilder().reporter(spans::add).build().tracer();
  Span span = tracer.newTrace();

  @Test public void isNotNoop() {
    assertThat(span.isNoop()).isFalse();
  }

  @Test public void hasRealContext() {
    assertThat(span.context().spanId()).isNotZero();
  }

  @Test public void start() {
    span.start();
    span.flush();

    assertThat(spans).hasSize(1).first()
        .extracting(s -> s.timestamp)
        .isNotNull();
  }

  @Test public void start_timestamp() {
    span.start(2);
    span.flush();

    assertThat(spans).hasSize(1).first()
        .extracting(s -> s.timestamp)
        .containsExactly(2L);
  }

  @Test public void finish() {
    span.start();
    span.finish();

    assertThat(spans).hasSize(1).first()
        .extracting(s -> s.duration)
        .isNotNull();
  }

  @Test public void finish_timestamp() {
    span.start(2);
    span.finish(5);

    assertThat(spans).hasSize(1).first()
        .extracting(s -> s.duration)
        .containsExactly(3L);
  }

  @Test public void annotate() {
    span.annotate("foo");
    span.flush();

    assertThat(spans).flatExtracting(s -> s.annotations)
        .extracting(a -> a.value, a -> a.endpoint)
        .containsExactly(tuple("foo", localEndpoint));
  }

  @Test public void annotate_timestamp() {
    span.annotate(2, "foo");
    span.flush();

    assertThat(spans).flatExtracting(s -> s.annotations)
        .containsExactly(Annotation.create(2L, "foo", localEndpoint));
  }

  @Test public void tag() {
    span.tag("foo", "bar");
    span.flush();

    assertThat(spans).flatExtracting(s -> s.binaryAnnotations)
        .containsExactly(BinaryAnnotation.create("foo", "bar", localEndpoint));
  }

  @Test public void doubleFinishDoesntDoubleReport() {
    Span span = tracer.newTrace().name("foo").start();

    span.finish();
    span.finish();

    assertThat(spans).hasSize(1);
  }
}

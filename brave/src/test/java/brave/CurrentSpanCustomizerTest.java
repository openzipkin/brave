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

public class CurrentSpanCustomizerTest {

  Endpoint localEndpoint = Platform.get().localEndpoint();
  List<zipkin.Span> spans = new ArrayList();
  Tracing tracing = Tracing.newBuilder().reporter(spans::add).build();
  CurrentSpanCustomizer spanCustomizer = CurrentSpanCustomizer.create(tracing);
  Span span = tracing.tracer().newTrace();

  @Test public void name() {
    span.start();
    try (Tracer.SpanInScope ws = tracing.tracer().withSpanInScope(span)) {
      spanCustomizer.name("newname");
    }
    span.flush();

    assertThat(spans).extracting(s -> s.name)
        .containsExactly("newname");
  }

  @Test public void name_when_no_current_span() {
    spanCustomizer.name("newname");
  }

  @Test public void tag() {
    span.start();
    try (Tracer.SpanInScope ws = tracing.tracer().withSpanInScope(span)) {
      spanCustomizer.tag("foo", "bar");
    }
    span.flush();

    assertThat(spans).flatExtracting(s -> s.binaryAnnotations)
        .containsExactly(BinaryAnnotation.create("foo", "bar", localEndpoint));
  }

  @Test public void tag_when_no_current_span() {
    spanCustomizer.tag("foo", "bar");
  }

  @Test public void annotate() {
    span.start();
    try (Tracer.SpanInScope ws = tracing.tracer().withSpanInScope(span)) {
      spanCustomizer.annotate("foo");
    }
    span.flush();

    assertThat(spans).flatExtracting(s -> s.annotations)
        .extracting(a -> a.value, a -> a.endpoint)
        .containsExactly(tuple("foo", localEndpoint));
  }

  @Test public void annotate_when_no_current_span() {
    spanCustomizer.annotate("foo");
  }

  @Test public void annotate_timestamp() {
    span.start();
    try (Tracer.SpanInScope ws = tracing.tracer().withSpanInScope(span)) {
      spanCustomizer.annotate(2, "foo");
    }
    span.flush();

    assertThat(spans).flatExtracting(s -> s.annotations)
        .containsExactly(Annotation.create(2L, "foo", localEndpoint));
  }

  @Test public void annotate_timestamp_when_no_current_span() {
    spanCustomizer.annotate(2, "foo");
  }
}

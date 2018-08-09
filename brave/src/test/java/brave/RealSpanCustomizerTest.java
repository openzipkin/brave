package brave;

import brave.propagation.ThreadLocalCurrentTraceContext;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import zipkin2.Annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class RealSpanCustomizerTest {
  List<zipkin2.Span> spans = new ArrayList();
  Tracing tracing = Tracing.newBuilder()
      .currentTraceContext(ThreadLocalCurrentTraceContext.create())
      .spanReporter(spans::add)
      .build();
  Span span = tracing.tracer().newTrace();
  SpanCustomizer spanCustomizer = span.customizer();

  @After public void close() {
    tracing.close();
  }

  @Test public void name() {
    spanCustomizer.name("foo");
    span.flush();

    assertThat(spans).extracting(zipkin2.Span::name)
        .containsExactly("foo");
  }

  @Test public void annotate() {
    spanCustomizer.annotate("foo");
    span.flush();

    assertThat(spans).flatExtracting(zipkin2.Span::annotations)
        .extracting(Annotation::value)
        .containsExactly("foo");
  }

  @Test public void tag() {
    spanCustomizer.tag("foo", "bar");
    span.flush();

    assertThat(spans).flatExtracting(s -> s.tags().entrySet())
        .containsExactly(entry("foo", "bar"));
  }
}

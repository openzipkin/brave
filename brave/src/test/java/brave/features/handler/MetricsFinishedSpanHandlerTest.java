package brave.features.handler;

import brave.Tracing;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

public class MetricsFinishedSpanHandlerTest {
  SimpleMeterRegistry registry = new SimpleMeterRegistry();
  List<Span> spans = new ArrayList<>();
  Tracing tracing = Tracing.newBuilder()
      .spanReporter(spans::add)
      .addFinishedSpanHandler(new MetricsFinishedSpanHandler(registry, "span", "foo"))
      .build();

  @After public void after() {
    tracing.close();
    registry.close();
  }

  @Test public void onlyRecordsSpansMatchingSpanName() {
    tracing.tracer().nextSpan().name("foo").start().finish();
    tracing.tracer().nextSpan().name("bar").start().finish();
    tracing.tracer().nextSpan().name("foo").start().finish();

    assertThat(registry.get("span")
        .tags("name", "foo", "exception", "None").timer().count())
        .isEqualTo(2L);

    try {
      registry.get("span").tags("name", "bar", "exception", "None").timer();

      failBecauseExceptionWasNotThrown(MeterNotFoundException.class);
    } catch (MeterNotFoundException expected) {
    }
  }

  @Test public void addsExceptionTagToSpan() {
    tracing.tracer().nextSpan().name("foo").start()
        .tag("error", "wow")
        .error(new IllegalStateException())
        .finish();

    assertThat(registry.get("span")
        .tags("name", "foo", "exception", "IllegalStateException").timer().count())
        .isEqualTo(1L);
    assertThat(spans.get(0).tags())
        .containsEntry("exception", "IllegalStateException");
  }
}

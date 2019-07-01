package brave;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SpanCustomizerShieldTest {
  Tracing tracing = Tracing.newBuilder().build();

  @Test public void doesNotStackOverflowOnToString() {
    Span span = tracing.tracer().newTrace();
    SpanCustomizerShield shield = new SpanCustomizerShield(span);
    assertThat(shield.toString())
      .isNotEmpty()
      .isEqualTo("SpanCustomizer(RealSpan(" + span.context().traceIdString() + "/" + span.context().spanIdString() + "))");
  }
}

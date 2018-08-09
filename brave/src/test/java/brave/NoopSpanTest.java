package brave;

import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.sampler.Sampler;
import org.junit.After;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NoopSpanTest {
  Tracer tracer = Tracing.newBuilder().sampler(Sampler.NEVER_SAMPLE)
      .currentTraceContext(ThreadLocalCurrentTraceContext.create())
      .clock(() -> {
        throw new AssertionError();
      })
      .spanReporter(s -> {
        throw new AssertionError();
      })
      .build().tracer();
  Span span = tracer.newTrace();

  @After public void close() {
    Tracing.current().close();
  }

  @Test public void isNoop() {
    assertThat(span.isNoop()).isTrue();
  }

  @Test public void hasRealContext() {
    assertThat(span.context().spanId()).isNotZero();
  }

  @Test public void hasNoopCustomizer() {
    assertThat(span.customizer()).isSameAs(NoopSpanCustomizer.INSTANCE);
  }

  @Test public void doesNothing() {
    // Since our clock and spanReporter throw, we know this is doing nothing
    span.start();
    span.start(1L);
    span.annotate("foo");
    span.annotate(2L, "foo");
    span.tag("bar", "baz");
    span.remoteServiceName("aloha");
    span.remoteIpAndPort("1.2.3.4", 9000);
    span.finish(1L);
    span.finish();
    span.abandon();
    span.flush();
  }
}

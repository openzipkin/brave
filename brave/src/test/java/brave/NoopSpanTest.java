package brave;

import brave.sampler.Sampler;
import org.junit.Test;
import zipkin.Endpoint;

import static org.assertj.core.api.Assertions.assertThat;

public class NoopSpanTest {
  Tracer tracer = Tracer.newBuilder().sampler(Sampler.NEVER_SAMPLE)
      .clock(() -> {
        throw new AssertionError();
      })
      .reporter(s -> {
        throw new AssertionError();
      })
      .build();
  Span span = tracer.newTrace();

  @Test public void isNoop() {
    assertThat(span.isNoop()).isTrue();
  }

  @Test public void hasRealContext() {
    assertThat(span.context().spanId()).isNotZero();
  }

  @Test public void doesNothing() {
    // Since our clock and reporter throw, we know this is doing nothing
    span.start();
    span.start(1L);
    span.annotate("foo");
    span.annotate(2L, "foo");
    span.tag("bar", "baz");
    span.remoteEndpoint(Endpoint.create("lalala", 127 << 24 | 1));
    span.finish(1L);
    span.finish();
    span.flush();
  }
}

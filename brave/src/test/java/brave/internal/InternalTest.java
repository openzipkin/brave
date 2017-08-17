package brave.internal;

import brave.Span;
import brave.Tracing;
import org.junit.After;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InternalTest {
  Tracing tracing = Tracing.newBuilder().build();

  @After public void close() {
    Tracing.current().close();
  }

  /**
   * Brave 3's LocalTracer.finish(duration) requires a read-back of the initial timestamp. While
   * that api is in use, this hook is needed.
   */
  @Test public void readBackTimestamp() {
    long timestamp = tracing.clock().currentTimeMicroseconds();

    Span span = tracing.tracer().newTrace().name("foo").start(timestamp);

    assertThat(Internal.instance.timestamp(tracing.tracer(), span.context()))
        .isEqualTo(timestamp);
  }
}

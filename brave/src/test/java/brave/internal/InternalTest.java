package brave.internal;

import brave.Span;
import brave.Tracer;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InternalTest {
  Tracer tracer = Tracer.newBuilder().build();

  /**
   * Brave 3's LocalTracer.finish(duration) requires a read-back of the initial timestamp. While
   * that api is in use, this hook is needed.
   */
  @Test public void readBackTimestamp() {
    long timestamp = tracer.clock().currentTimeMicroseconds();

    Span span = tracer.newTrace().name("foo").start(timestamp);

    assertThat(Internal.instance.timestamp(tracer, span.context()))
        .isEqualTo(timestamp);
  }
}

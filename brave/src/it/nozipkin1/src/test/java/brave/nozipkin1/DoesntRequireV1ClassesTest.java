package brave.nozipkin1;

import brave.Tracing;
import org.junit.Test;

public class DoesntRequireV1ClassesTest {
  /** Compiles and runs as long as we don't use any overloaded methods with zipkin v1 types */
  @Test public void doesntBreak() {
    try (Tracing tracing = Tracing.newBuilder()
        .endpoint(zipkin2.Endpoint.newBuilder().serviceName("foo").ip("1.2.3.4").build())
        .spanReporter(zipkin2.reporter.Reporter.NOOP)
        .build()) {
      tracing.tracer()
          .newTrace()
          .start()
          .finish();
    }
  }
}
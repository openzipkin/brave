package brave.internal.recorder;

import brave.propagation.TraceContext;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import zipkin2.Endpoint;
import zipkin2.reporter.Reporter;

import static brave.test.util.ClassLoaders.assertRunIsUnloadable;

public class SpanReporterClassLoaderTest {

  @Test public void unloadable_afterBasicUsage() {
    assertRunIsUnloadable(BasicUsage.class, getClass().getClassLoader());
  }

  static class BasicUsage implements Runnable {
    @Override public void run() {
      SpanReporter reporter = new SpanReporter(
          Endpoint.newBuilder().serviceName("unknown").build(),
          Reporter.NOOP,
          new AtomicBoolean());

      TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).build();
      MutableSpan span = new MutableSpan();
      span.name("get /users/{userId}");
      span.startTimestamp(1L);
      span.tag("http.method", "GET");
      span.annotate(2L, "cache.miss");
      span.finishTimestamp(3L);

      reporter.report(context, span);
    }
  }

  @Test public void unloadable_afterErrorReporting() {
    assertRunIsUnloadable(ErrorReporting.class, getClass().getClassLoader());
  }

  static class ErrorReporting implements Runnable {
    @Override public void run() {
      SpanReporter reporter = new SpanReporter(
          Endpoint.newBuilder().serviceName("unknown").build(),
          s -> {
            throw new RuntimeException();
          },
          new AtomicBoolean());

      reporter.report(TraceContext.newBuilder().traceId(1).spanId(2).build(), new MutableSpan());
    }
  }
}

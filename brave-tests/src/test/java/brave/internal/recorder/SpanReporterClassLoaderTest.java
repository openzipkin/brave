package brave.internal.recorder;

import brave.propagation.TraceContext;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import zipkin2.reporter.Reporter;

import static brave.test.util.ClassLoaders.assertRunIsUnloadable;

public class SpanReporterClassLoaderTest {

  @Test public void unloadable_afterBasicUsage() {
    assertRunIsUnloadable(BasicUsage.class, getClass().getClassLoader());
  }

  static class BasicUsage implements Runnable {
    @Override public void run() {
      MutableSpanConverter converter = new MutableSpanConverter("unknown", "127.0.0.1", 0);
      SpanReporter reporter = new SpanReporter(converter, Reporter.NOOP, new AtomicBoolean());

      TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true).build();
      MutableSpan span = new MutableSpan();
      span.name("get /users/{userId}");
      span.kind(brave.Span.Kind.CLIENT);
      span.startTimestamp(1L);
      span.tag("http.method", "GET");
      span.annotate(2L, "cache.miss");
      span.finishTimestamp(3L);

      reporter.accept(context, span);
    }
  }

  @Test public void unloadable_afterErrorReporting() {
    assertRunIsUnloadable(ErrorReporting.class, getClass().getClassLoader());
  }

  static class ErrorReporting implements Runnable {
    @Override public void run() {
      MutableSpanConverter converter = new MutableSpanConverter("unknown", "127.0.0.1", 0);
      SpanReporter reporter = new SpanReporter(converter, s -> {
        throw new RuntimeException();
      }, new AtomicBoolean());

      TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true).build();
      reporter.accept(context, new MutableSpan());
    }
  }
}

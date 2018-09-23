package brave.internal.recorder;

import brave.ErrorParser;
import brave.firehose.FirehoseHandler;
import brave.firehose.MutableSpan;
import brave.propagation.TraceContext;
import org.junit.Test;
import zipkin2.reporter.Reporter;

import static brave.test.util.ClassLoaders.assertRunIsUnloadable;

public class FirehoseDispatcherClassLoaderTest {

  @Test public void unloadable_afterBasicUsage() {
    assertRunIsUnloadable(BasicUsage.class, getClass().getClassLoader());
  }

  static class BasicUsage implements Runnable {
    @Override public void run() {
      FirehoseDispatcher firehoseDispatcher = new FirehoseDispatcher(new FirehoseHandler.Factory() {
        @Override public FirehoseHandler create(String serviceName, String ip, int port) {
          return FirehoseHandler.NOOP;
        }
      }, new ErrorParser(), Reporter.NOOP, "favistar", "1.2.3.4", 0);

      TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true).build();
      MutableSpan span = new MutableSpan();
      span.name("get /users/{userId}");
      span.kind(brave.Span.Kind.CLIENT);
      span.startTimestamp(1L);
      span.tag("http.method", "GET");
      span.annotate(2L, "cache.miss");
      span.finishTimestamp(3L);

      firehoseDispatcher.firehose().accept(context, span);
    }
  }

  @Test public void unloadable_afterErrorReporting() {
    assertRunIsUnloadable(ErrorReporting.class, getClass().getClassLoader());
  }

  static class ErrorReporting implements Runnable {
    @Override public void run() {
      FirehoseDispatcher firehoseDispatcher = new FirehoseDispatcher(new FirehoseHandler.Factory() {
        @Override public FirehoseHandler create(String serviceName, String ip, int port) {
          return FirehoseHandler.NOOP;
        }
      }, new ErrorParser(), s -> {
        throw new RuntimeException();
      }, "favistar", "1.2.3.4", 0);

      TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true).build();
      firehoseDispatcher.firehose().accept(context, new MutableSpan());
    }
  }
}

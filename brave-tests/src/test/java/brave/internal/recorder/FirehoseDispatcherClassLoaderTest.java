package brave.internal.recorder;

import brave.firehose.MutableSpan;
import brave.internal.firehose.FirehoseHandlers;
import brave.propagation.TraceContext;
import java.util.Arrays;
import org.junit.Test;

import static brave.test.util.ClassLoaders.assertRunIsUnloadable;

public class FirehoseDispatcherClassLoaderTest {

  @Test public void unloadable_afterBasicUsage() {
    assertRunIsUnloadable(BasicUsage.class, getClass().getClassLoader());
  }

  static class BasicUsage implements Runnable {
    @Override public void run() {
      FirehoseDispatcher firehoseDispatcher =
          new FirehoseDispatcher(Arrays.asList(FirehoseHandlers.constantFactory((c, s) -> {
          })), "favistar", "1.2.3.4", 0);

      TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true).build();
      MutableSpan span = new MutableSpan();
      span.name("get /users/{userId}");
      span.kind(brave.Span.Kind.CLIENT);
      span.startTimestamp(1L);
      span.tag("http.method", "GET");
      span.annotate(2L, "cache.miss");
      span.finishTimestamp(3L);

      firehoseDispatcher.firehoseHandler().handle(context, span);
    }
  }

  @Test public void unloadable_afterErrorReporting() {
    assertRunIsUnloadable(ErrorReporting.class, getClass().getClassLoader());
  }

  static class ErrorReporting implements Runnable {
    @Override public void run() {
      FirehoseDispatcher firehoseDispatcher =
          new FirehoseDispatcher(Arrays.asList(FirehoseHandlers.constantFactory((c, s) -> {
            throw new RuntimeException();
          })), "favistar", "1.2.3.4", 0);

      TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true).build();
      firehoseDispatcher.firehoseHandler().handle(context, new MutableSpan());
    }
  }
}

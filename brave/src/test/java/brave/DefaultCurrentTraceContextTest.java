package brave;

import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import java.util.concurrent.Callable;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultCurrentTraceContextTest {
  CurrentTraceContext.Default currentTraceContext = new CurrentTraceContext.Default();
  Tracer tracer = Tracing.newBuilder().build().tracer();
  TraceContext context = tracer.newTrace().context();
  TraceContext context2 = tracer.newTrace().context();

  @Test public void currentSpan_defaultsToNull() {
    assertThat(currentTraceContext.get()).isNull();
  }

  @Test public void scope_retainsContext() {
    try (CurrentTraceContext.Scope scope = currentTraceContext.newScope(context)) {
      assertThat(currentTraceContext.get())
          .isEqualTo(context);
    }
  }

  @Test public void scope_isDefinedPerThread() throws InterruptedException {
    final TraceContext[] threadValue = new TraceContext[1];

    try (CurrentTraceContext.Scope scope = currentTraceContext.newScope(context)) {
      Thread t = new Thread(() -> { // inheritable thread local
        assertThat(currentTraceContext.get())
            .isEqualTo(context);

        try (CurrentTraceContext.Scope scope2 = currentTraceContext.newScope(context2)) {
          assertThat(currentTraceContext.get())
              .isEqualTo(context2);
          threadValue[0] = context2;
        }
      });

      t.start();
      t.join();
      assertThat(currentTraceContext.get())
          .isEqualTo(context);
      assertThat(threadValue[0])
          .isEqualTo(context2);
    }
  }

  /**
   * Ensures default scope is per thread, not per thread,instance. This is needed when using {@link
   * Tracing#current()} such as instrumenting JDBC.
   */
  @Test public void perThreadScope() {
    try (CurrentTraceContext.Scope scope = currentTraceContext.newScope(context)) {
      assertThat(Tracing.current().tracer().currentSpan().context())
          .isEqualTo(context);
    }
  }

  @Test
  public void attachesSpanInCallable() throws Exception {
    Callable<?> callable;
    try (CurrentTraceContext.Scope scope = currentTraceContext.newScope(context)) {
      callable = currentTraceContext.wrap(() -> {
        assertThat(currentTraceContext.get())
            .isEqualTo(context);
        return true;
      });
    }

    // Set another span between the time the task was made and executed.
    try (CurrentTraceContext.Scope scope2 = currentTraceContext.newScope(context2)) {
      callable.call(); // runs assertion
    }
  }

  @Test
  public void restoresSpanAfterCallable() throws Exception {
    TraceContext context0 = tracer.newTrace().context();
    try (CurrentTraceContext.Scope scope0 = currentTraceContext.newScope(context0)) {
      attachesSpanInCallable();
      assertThat(currentTraceContext.get())
          .isEqualTo(context0);
    }
  }

  @Test
  public void attachesSpanInRunnable() throws Exception {
    Runnable runnable;
    try (CurrentTraceContext.Scope scope = currentTraceContext.newScope(context)) {
      runnable = currentTraceContext.wrap(() -> {
        assertThat(currentTraceContext.get())
            .isEqualTo(context);
      });
    }

    // Set another span between the time the task was made and executed.
    try (CurrentTraceContext.Scope scope2 = currentTraceContext.newScope(context2)) {
      runnable.run(); // runs assertion
    }
  }

  @Test
  public void restoresSpanAfterRunnable() throws Exception {
    TraceContext context0 = tracer.newTrace().context();
    try (CurrentTraceContext.Scope scope0 = currentTraceContext.newScope(context0)) {
      attachesSpanInRunnable();
      assertThat(currentTraceContext.get())
          .isEqualTo(context0);
    }
  }
}

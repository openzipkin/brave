package brave.propagation;

import brave.internal.Nullable;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class CurrentTraceContextTest {

  protected abstract CurrentTraceContext newCurrentTraceContext();

  protected final CurrentTraceContext currentTraceContext;
  final TraceContext context;
  final TraceContext context2;

  protected CurrentTraceContextTest() {
    currentTraceContext = newCurrentTraceContext();
    context = TraceContext.newBuilder().traceId(1L).spanId(1L).build();
    context2 = TraceContext.newBuilder().traceId(1L).parentId(1L).spanId(2L).build();
  }

  protected void verifyImplicitContext(@Nullable TraceContext context) {
  }

  @Test public void currentSpan_defaultsToNull() {
    assertThat(currentTraceContext.get()).isNull();
  }

  @Test public void scope_retainsContext() {
    try (CurrentTraceContext.Scope scope = currentTraceContext.newScope(context)) {
      assertThat(currentTraceContext.get())
          .isEqualTo(context);
      verifyImplicitContext(context);
    }
  }

  @Test public void scope_canClearScope() {
    try (CurrentTraceContext.Scope scope = currentTraceContext.newScope(context)) {
      try (CurrentTraceContext.Scope noScope = currentTraceContext.newScope(null)) {
        assertThat(currentTraceContext.get())
            .isNull();
        verifyImplicitContext(null);
      }

      // old context reverted
      assertThat(currentTraceContext.get())
          .isEqualTo(context);
      verifyImplicitContext(context);
    }
  }

  protected void is_inheritable(CurrentTraceContext inheritableCurrentTraceContext)
      throws Exception {
    // use a single-threaded version of newCachedThreadPool
    ExecutorService service = new ThreadPoolExecutor(0, 1,
        60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

    // submitting a job grows the pool, attaching the context to its thread
    try (CurrentTraceContext.Scope scope = inheritableCurrentTraceContext.newScope(context)) {
      assertThat(service.submit(() -> inheritableCurrentTraceContext.get()).get())
          .isEqualTo(context);
    }

    // same thread executes the next job and still has the same context (leaked and not cleaned up)
    assertThat(service.submit(() -> inheritableCurrentTraceContext.get()).get())
        .isEqualTo(context);

    service.shutdownNow();
  }

  @Test public void isnt_inheritable() throws Exception {
    ExecutorService service = Executors.newCachedThreadPool();

    try (CurrentTraceContext.Scope scope = currentTraceContext.newScope(context)) {
      assertThat(service.submit(() -> {
        verifyImplicitContext(null);
        return currentTraceContext.get();
      }).get()).isNull();
    } catch (ExecutionException e) {
      if (e.getCause() instanceof Error) throw (Error) e.getCause();
      throw (Exception) e.getCause();
    }

    assertThat(service.submit(() -> currentTraceContext.get()).get())
        .isNull();
    verifyImplicitContext(null);

    service.shutdownNow();
  }

  @Test public void attachesSpanInCallable() throws Exception {
    Callable<?> callable;
    try (CurrentTraceContext.Scope scope = currentTraceContext.newScope(context)) {
      callable = currentTraceContext.wrap(() -> {
        assertThat(currentTraceContext.get())
            .isEqualTo(context);
        verifyImplicitContext(context);
        return true;
      });
    }

    // Set another span between the time the task was made and executed.
    try (CurrentTraceContext.Scope scope2 = currentTraceContext.newScope(context2)) {
      callable.call(); // runs assertion
      verifyImplicitContext(context2);
    }
  }

  @Test public void restoresSpanAfterCallable() throws Exception {
    try (CurrentTraceContext.Scope scope0 = currentTraceContext.newScope(context)) {
      attachesSpanInCallable();
      assertThat(currentTraceContext.get())
          .isEqualTo(context);
      verifyImplicitContext(context);
    }
  }

  @Test public void attachesSpanInRunnable() throws Exception {
    Runnable runnable;
    try (CurrentTraceContext.Scope scope = currentTraceContext.newScope(context)) {
      runnable = currentTraceContext.wrap(() -> {
        assertThat(currentTraceContext.get())
            .isEqualTo(context);
        verifyImplicitContext(context);
      });
    }

    // Set another span between the time the task was made and executed.
    try (CurrentTraceContext.Scope scope2 = currentTraceContext.newScope(context2)) {
      runnable.run(); // runs assertion
      verifyImplicitContext(context2);
    }
  }

  @Test public void restoresSpanAfterRunnable() throws Exception {
    TraceContext context0 = TraceContext.newBuilder().traceId(3L).spanId(3L).build();

    try (CurrentTraceContext.Scope scope0 = currentTraceContext.newScope(context0)) {
      attachesSpanInRunnable();
      assertThat(currentTraceContext.get())
          .isEqualTo(context0);
      verifyImplicitContext(context0);
    }
  }
}

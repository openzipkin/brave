package brave.internal;

import brave.Tracer;
import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StrictCurrentTraceContextTest {
  ExecutorService executor = Executors.newSingleThreadExecutor();
  // override default so that it isn't inheritable
  CurrentTraceContext currentTraceContext = new StrictCurrentTraceContext();

  Tracer tracer = Tracing.newBuilder().build().tracer();
  TraceContext context = tracer.newTrace().context();
  TraceContext context2 = tracer.newTrace().context();

  @After public void close() {
    Tracing.current().close();
  }

  @After public void after() throws InterruptedException {
    executor.shutdownNow();
    executor.awaitTermination(1, TimeUnit.SECONDS);
  }

  /**
   * When using strict current context, scope is per thread,instance. Interactions like JDBC will
   * need to share a static instance explicitly.
   */
  @Test public void instancesAreIndependent() {
    CurrentTraceContext currentTraceContext2 = new StrictCurrentTraceContext();

    try (CurrentTraceContext.Scope scope1 = currentTraceContext.newScope(context)) {
      assertThat(currentTraceContext2.get()).isNull();

      try (CurrentTraceContext.Scope scope2 = currentTraceContext2.newScope(context2)) {
        assertThat(currentTraceContext.get()).isEqualTo(context);
        assertThat(currentTraceContext2.get()).isEqualTo(context2);
      }
    }
  }

  @Test public void scope_isNotInheritable() throws InterruptedException {
    final TraceContext[] threadValue = new TraceContext[1];

    try (CurrentTraceContext.Scope scope = currentTraceContext.newScope(context)) {
      Thread t = new Thread(() -> { // should not inherit scope!
        threadValue[0] = currentTraceContext.get();
      });

      t.start();
      t.join();
      assertThat(threadValue[0]).isNull();
    }
  }

  @Test public void scope_canClearScope() {
    try (CurrentTraceContext.Scope scope = currentTraceContext.newScope(context)) {
      try (CurrentTraceContext.Scope noScope = currentTraceContext.newScope(null)) {
        assertThat(currentTraceContext.get())
            .isNull();
      }

      // old context reverted
      assertThat(currentTraceContext.get())
          .isEqualTo(context);
    }
  }

  @Test public void scope_enforcesCloseOnSameThread() throws InterruptedException {
    final Exception[] spawnedThreadException = new Exception[1];
    Thread scopingThread = new Thread(() -> {
      try (CurrentTraceContext.Scope scope = currentTraceContext.newScope(context)) {
        Thread spawnedThread = new Thread(() -> { // should not inherit scope!
          try {
            scope.close();
          } catch (IllegalStateException e) {
            spawnedThreadException[0] = e;
          }
        }, "spawned thread");
        spawnedThread.start();
        spawnedThread.join();
      } catch (InterruptedException e) {
        e.printStackTrace();
        Thread.currentThread().interrupt();
      }
    }, "scoping thread");

    scopingThread.start();
    scopingThread.join();

    assertThat(spawnedThreadException[0])
        .hasMessage("scope closed in a different thread: spawned thread");
    assertThat(spawnedThreadException[0].getCause())
        .hasMessage("Thread scoping thread opened scope for " + context + " here:");
  }
}

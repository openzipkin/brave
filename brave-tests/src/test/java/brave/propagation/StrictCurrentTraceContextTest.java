package brave.propagation;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StrictCurrentTraceContextTest extends CurrentTraceContextTest {

  @Override protected CurrentTraceContext newCurrentTraceContext() {
    return new StrictCurrentTraceContext();
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

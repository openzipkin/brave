/*
 * Copyright 2013-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.propagation;

import brave.test.propagation.CurrentTraceContextTest;
import java.util.function.Supplier;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StrictCurrentTraceContextTest extends CurrentTraceContextTest {

  @Override protected Class<? extends Supplier<CurrentTraceContext>> currentSupplier() {
    return CurrentSupplier.class;
  }

  static class CurrentSupplier implements Supplier<CurrentTraceContext> {
    @Override public CurrentTraceContext get() {
      return new StrictCurrentTraceContext();
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

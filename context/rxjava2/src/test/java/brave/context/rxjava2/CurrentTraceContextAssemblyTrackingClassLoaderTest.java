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
package brave.context.rxjava2;

import brave.context.rxjava2.CurrentTraceContextAssemblyTracking.SavedHooks;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.propagation.TraceContext;
import io.reactivex.Observable;
import org.junit.Test;

import static brave.test.util.ClassLoaders.assertRunIsUnloadable;

public class CurrentTraceContextAssemblyTrackingClassLoaderTest {

  @Test public void noop_unloadable() {
    assertRunIsUnloadable(Noop.class, getClass().getClassLoader());
  }

  static class Noop implements Runnable {
    @Override public void run() {
      new CurrentTraceContextAssemblyTracking(
        ThreadLocalCurrentTraceContext.newBuilder().build()
      ).enable();
      CurrentTraceContextAssemblyTracking.disable();
    }
  }

  /** Proves when code is correct, we can unload our classes. */
  @Test public void simpleUsage_unloadable() {
    assertRunIsUnloadable(SimpleUsable.class, getClass().getClassLoader());
  }

  static class SimpleUsable implements Runnable {
    @Override public void run() {
      CurrentTraceContext currentTraceContext =
        ThreadLocalCurrentTraceContext.newBuilder().build();
      SavedHooks saved = new CurrentTraceContextAssemblyTracking(currentTraceContext)
        .enableAndChain();

      TraceContext assembly = TraceContext.newBuilder().traceId(1).spanId(1).build();

      try (Scope scope = currentTraceContext.newScope(assembly)) {
        Observable.just(1).map(i -> i).test().assertNoErrors();
      } finally {
        saved.restore();
      }
    }
  }
}

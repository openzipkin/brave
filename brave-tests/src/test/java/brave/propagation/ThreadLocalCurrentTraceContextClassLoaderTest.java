/*
 * Copyright 2013-2020 The OpenZipkin Authors
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

import brave.propagation.CurrentTraceContext.Scope;
import org.junit.Test;

import static brave.test.util.ClassLoaders.assertRunIsUnloadable;

public class ThreadLocalCurrentTraceContextClassLoaderTest {

  @Test public void unused_unloadable() {
    assertRunIsUnloadable(Unused.class, getClass().getClassLoader());
  }

  static class Unused implements Runnable {
    @Override public void run() {
      ThreadLocalCurrentTraceContext.newBuilder().build();
    }
  }

  @Test public void currentTracer_basicUsage_unloadable() {
    assertRunIsUnloadable(BasicUsage.class, getClass().getClassLoader());
  }

  static class BasicUsage implements Runnable {
    @Override public void run() {
      CurrentTraceContext current = ThreadLocalCurrentTraceContext.newBuilder().build();
      try (Scope scope = current.newScope(TraceContext.newBuilder().traceId(1).spanId(1).build())) {

      }
    }
  }

  @Test public void leakedNullScope() {
    assertRunIsUnloadable(LeakedNullScope.class, getClass().getClassLoader());
  }

  static class LeakedNullScope implements Runnable {
    @Override public void run() {
      CurrentTraceContext current = ThreadLocalCurrentTraceContext.newBuilder().build();
      current.newScope(null);
    }
  }

  /**
   * TODO: While it is an instrumentation bug to leak a scope, we should be tolerant.
   *
   * <p>The current design problem is we don't know a reference type we can use that clears when
   * the classloader is unloaded, regardless of GC. For example, having {@link Scope} extend {@link
   * java.lang.ref.WeakReference} to hold the value to revert. This would only help if GC happened
   * prior to the classloader unload, which would be an odd thing to rely on.
   */
  @Test(expected = AssertionError.class) public void leakedScope_preventsUnloading() {
    assertRunIsUnloadable(LeakedScope.class, getClass().getClassLoader());
  }

  static class LeakedScope implements Runnable {
    @Override public void run() {
      CurrentTraceContext current = ThreadLocalCurrentTraceContext.newBuilder().build();
      current.newScope(TraceContext.newBuilder().traceId(1).spanId(1).build());
    }
  }
}

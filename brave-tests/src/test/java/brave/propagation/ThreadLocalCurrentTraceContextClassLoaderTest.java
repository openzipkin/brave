/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.propagation;

import brave.propagation.CurrentTraceContext.Scope;
import org.junit.jupiter.api.Test;

import static brave.test.util.ClassLoaders.assertRunIsUnloadable;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ThreadLocalCurrentTraceContextClassLoaderTest {

  @Test void unused_unloadable() {
    assertRunIsUnloadable(Unused.class, getClass().getClassLoader());
  }

  static class Unused implements Runnable {
    @Override public void run() {
      ThreadLocalCurrentTraceContext.newBuilder().build();
    }
  }

  @Test void currentTracer_basicUsage_unloadable() {
    assertRunIsUnloadable(BasicUsage.class, getClass().getClassLoader());
  }

  static class BasicUsage implements Runnable {
    @Override public void run() {
      CurrentTraceContext current = ThreadLocalCurrentTraceContext.newBuilder().build();
      try (Scope scope = current.newScope(TraceContext.newBuilder().traceId(1).spanId(1).build())) {

      }
    }
  }

  @Test void leakedNullScope() {
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
  @Test void leakedScope_preventsUnloading() {
    assertThrows(AssertionError.class, () -> {
      assertRunIsUnloadable(LeakedScope.class, getClass().getClassLoader());
    });
  }

  static class LeakedScope implements Runnable {
    @Override public void run() {
      CurrentTraceContext current = ThreadLocalCurrentTraceContext.newBuilder().build();
      current.newScope(TraceContext.newBuilder().traceId(1).spanId(1).build());
    }
  }
}

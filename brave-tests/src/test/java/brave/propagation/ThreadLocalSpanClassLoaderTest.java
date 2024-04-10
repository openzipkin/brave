/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.propagation;

import brave.Tracing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static brave.test.util.ClassLoaders.assertRunIsUnloadable;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ThreadLocalSpanClassLoaderTest {

  @BeforeEach void ensureNothingCurrent(){
    Tracing current = Tracing.current();
    if (current != null) current.close();
  }

  @Test void noop_unloadable() {
    assertRunIsUnloadable(CurrentTracerUnassigned.class, getClass().getClassLoader());
  }

  static class CurrentTracerUnassigned implements Runnable {
    @Override public void run() {
      ThreadLocalSpan.CURRENT_TRACER.next();
    }
  }

  @Test void currentTracer_basicUsage_unloadable() {
    assertRunIsUnloadable(CurrentTracerBasicUsage.class, getClass().getClassLoader());
  }

  static class CurrentTracerBasicUsage implements Runnable {
    @Override public void run() {
      try (Tracing tracing = Tracing.newBuilder().build()) {
        ThreadLocalSpan tlSpan = ThreadLocalSpan.CURRENT_TRACER;

        tlSpan.next();
        tlSpan.remove().finish();
      }
    }
  }

  @Test void explicitTracer_basicUsage_unloadable() {
    assertRunIsUnloadable(ExplicitTracerBasicUsage.class, getClass().getClassLoader());
  }

  static class ExplicitTracerBasicUsage implements Runnable {
    @Override public void run() {
      try (Tracing tracing = Tracing.newBuilder().build()) {
        ThreadLocalSpan tlSpan = ThreadLocalSpan.create(tracing.tracer());

        tlSpan.next();
        tlSpan.remove().finish();
      }
    }
  }

  /**
   * TODO: While it is an instrumentation bug to not complete a thread-local span, we should be
   * tolerant, for example considering weak references or similar.
   */
  @Test void unfinishedSpan_preventsUnloading() {
    assertThrows(AssertionError.class, () -> {
      assertRunIsUnloadable(CurrentTracerDoesntFinishSpan.class, getClass().getClassLoader());
    });
  }

  static class CurrentTracerDoesntFinishSpan implements Runnable {
    @Override public void run() {
      try (Tracing tracing = Tracing.newBuilder().build()) {
        ThreadLocalSpan.CURRENT_TRACER.next();
      }
    }
  }
}

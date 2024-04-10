/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave;

import java.lang.ref.WeakReference;

/**
 * Utilities for working with the garbage collector in tests.
 */
public final class GarbageCollectors {

  /**
   * Runs the garbage collector and waits until all of the provided {@link WeakReference} are
   * cleared, indicating the referenced objects have been collected.
   */
  public static void blockOnGC() {
    System.gc();
    try {
      Thread.sleep(200);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }

  private GarbageCollectors() {}
}

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

package brave.test.util;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

import static org.awaitility.Awaitility.await;

/**
 * Utilities for working with the garbage collector in tests.
 */
public final class GarbageCollectors {

  /**
   * Runs the garbage collector and waits until all of the provided {@link WeakReference} are
   * cleared, indicating the referenced objects have been collected.
   */
  public static void blockOnGC(WeakReference<?>... cleared) {
    // WeakReference doesn't provide a way to know if the reference is cleared or not without
    // creating a new reference. So we copy into a WeakHashMap which does let us know.
    WeakHashMap<Object, Boolean> map = new WeakHashMap<>();
    for (WeakReference<?> reference : cleared) {
      Object obj = reference.get();
      if (obj != null) {
        map.put(obj, true);
        obj = null;
      }
    }

    System.gc();

    try {
      await().until(map::isEmpty);
    } catch (Throwable t) {
      System.out.println(map);
    }
  }

  private GarbageCollectors() {}
}

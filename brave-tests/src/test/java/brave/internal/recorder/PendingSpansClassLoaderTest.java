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
package brave.internal.recorder;

import brave.handler.FinishedSpanHandler;
import brave.handler.MutableSpan;
import brave.internal.Platform;
import brave.propagation.TraceContext;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

import static brave.test.util.ClassLoaders.assertRunIsUnloadable;

public class PendingSpansClassLoaderTest {

  @Test public void unloadable_afterCreateAndRemove() {
    assertRunIsUnloadable(CreateAndRemove.class, getClass().getClassLoader());
  }

  static class CreateAndRemove implements Runnable {
    @Override public void run() {
      PendingSpans pendingSpans =
        new PendingSpans(Platform.get().clock(), new FinishedSpanHandler() {
          @Override public boolean handle(TraceContext context, MutableSpan span) {
            return true;
          }
        }, new AtomicBoolean());

      TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).build();
      pendingSpans.getOrCreate(context, true);
      pendingSpans.remove(context);
    }
  }

  @Test public void unloadable_afterOrphan() {
    assertRunIsUnloadable(OrphanedContext.class, getClass().getClassLoader());
  }

  static class OrphanedContext implements Runnable {

    @Override public void run() {
      PendingSpans pendingSpans =
        new PendingSpans(Platform.get().clock(), new FinishedSpanHandler() {
          @Override public boolean handle(TraceContext context, MutableSpan span) {
            return true;
          }
        }, new AtomicBoolean());

      TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).build();
      pendingSpans.getOrCreate(context, true);
      context = null; // orphan the context

      System.gc();
      try {
        // We usually block on a weak reference being cleared, but here we would end up loading
        // the WeakReference class from the classloader preventing it from unloading.
        Thread.sleep(200);
      } catch (InterruptedException e) {
        throw new AssertionError(e);
      }

      pendingSpans.reportOrphanedSpans();
    }
  }
}

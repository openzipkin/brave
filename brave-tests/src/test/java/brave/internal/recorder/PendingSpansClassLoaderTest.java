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
package brave.internal.recorder;

import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.internal.InternalPropagation;
import brave.internal.Platform;
import brave.internal.handler.OrphanTracker;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import brave.test.util.GarbageCollectors;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

import static brave.internal.InternalPropagation.FLAG_LOCAL_ROOT;
import static brave.internal.InternalPropagation.FLAG_SAMPLED;
import static brave.internal.InternalPropagation.FLAG_SAMPLED_SET;
import static brave.test.util.ClassLoaders.assertRunIsUnloadable;

public class PendingSpansClassLoaderTest {
  static {
    SamplingFlags.NOT_SAMPLED.toString(); // ensure InternalPropagation is wired for tests
  }

  // PendingSpans should always be passed a trace context instantiated by the Tracer. This fakes
  // a local root span, so that we don't have to depend on the Tracer to run these tests.
  static final TraceContext CONTEXT = InternalPropagation.instance.newTraceContext(
    FLAG_SAMPLED_SET | FLAG_SAMPLED | FLAG_LOCAL_ROOT,
    0L,
    1L,
    2L,
    0L,
    1L,
    Collections.emptyList()
  );

  @Test public void unloadable_afterCreateAndRemove() {
    assertRunIsUnloadable(CreateAndRemove.class, getClass().getClassLoader());
  }

  static class CreateAndRemove implements Runnable {
    @Override public void run() {
      PendingSpans pendingSpans = new PendingSpans(new MutableSpan(),
        Platform.get().clock(), SpanHandler.NOOP, new AtomicBoolean());

      TraceContext context = CONTEXT.toBuilder().build(); // intentionally make a copy
      pendingSpans.getOrCreate(null, context, true);
      pendingSpans.remove(context);
    }
  }

  @Test public void unloadable_afterOrphan() {
    assertRunIsUnloadable(OrphanedContext.class, getClass().getClassLoader());
  }

  static class OrphanedContext implements Runnable {
    @Override public void run() {
      PendingSpans pendingSpans = new PendingSpans(new MutableSpan(),
        Platform.get().clock(), new OrphanTracker(Platform.get().clock()), new AtomicBoolean());

      TraceContext context = CONTEXT.toBuilder().build(); // intentionally make a copy
      pendingSpans.getOrCreate(null, context, true);
      context = null; // orphan the context

      GarbageCollectors.blockOnGC();

      pendingSpans.expungeStaleEntries();
    }
  }
}

/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.internal.recorder;

import brave.Clock;
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
import org.junit.jupiter.api.Test;

import static brave.internal.InternalPropagation.FLAG_LOCAL_ROOT;
import static brave.internal.InternalPropagation.FLAG_SAMPLED;
import static brave.internal.InternalPropagation.FLAG_SAMPLED_SET;
import static brave.test.util.ClassLoaders.assertRunIsUnloadable;

class PendingSpansClassLoaderTest {
  static {
    String unused = SamplingFlags.DEBUG.toString(); // ensure InternalPropagation is wired for tests
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

  @Test void unloadable_afterCreateAndRemove() {
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

  @Test void unloadable_afterOrphan() {
    assertRunIsUnloadable(OrphanedContext.class, getClass().getClassLoader());
  }

  static class OrphanedContext implements Runnable {
    @Override public void run() {
      MutableSpan defaultSpan = new MutableSpan();
      Clock clock = Platform.get().clock();
      SpanHandler orphanTracker =
          OrphanTracker.newBuilder().clock(clock).defaultSpan(defaultSpan).build();
      PendingSpans pendingSpans =
          new PendingSpans(defaultSpan, clock, orphanTracker, new AtomicBoolean());

      TraceContext context = CONTEXT.toBuilder().build(); // intentionally make a copy
      pendingSpans.getOrCreate(null, context, true);
      context = null; // orphan the context

      GarbageCollectors.blockOnGC();

      pendingSpans.expungeStaleEntries();
    }
  }
}

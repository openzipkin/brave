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
import brave.internal.InternalPropagation;
import brave.propagation.TraceContext;
import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;
import zipkin2.Annotation;
import zipkin2.Span;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class PendingSpansTest {
  List<zipkin2.Span> spans = new ArrayList<>();
  TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true).build();
  AtomicInteger clock = new AtomicInteger();
  PendingSpans pendingSpans;

  @Before public void init() {
    init(new FinishedSpanHandler() {
      @Override public boolean handle(TraceContext ctx, MutableSpan span) {
        if (!Boolean.TRUE.equals(ctx.sampled())) return true;

        Span.Builder b = Span.newBuilder().traceId(ctx.traceIdString()).id(ctx.traceIdString());
        b.name(span.name());
        span.forEachAnnotation(Span.Builder::addAnnotation, b);
        spans.add(b.build());
        return true;
      }
    });
  }

  void init(FinishedSpanHandler zipkinFinishedSpanHandler) {
    pendingSpans = new PendingSpans(() -> clock.incrementAndGet() * 1000L,
      zipkinFinishedSpanHandler,
      new AtomicBoolean());
  }

  @Test
  public void getOrCreate_lazyCreatesASpan() {
    PendingSpan span = pendingSpans.getOrCreate(context, false);

    assertThat(span).isNotNull();
  }

  /** Ensure we use the same clock for traces that started in-process */
  @Test
  public void getOrCreate_reusesClockFromParent() {
    TraceContext trace = TraceContext.newBuilder().traceId(1L).spanId(2L).build();
    TraceContext traceJoin = trace.toBuilder().shared(true).build();
    TraceContext trace2 = TraceContext.newBuilder().traceId(2L).spanId(2L).build();
    TraceContext traceChild =
      TraceContext.newBuilder().traceId(1L).parentId(2L).spanId(3L).build();

    PendingSpan traceSpan = pendingSpans.getOrCreate(trace, false);
    PendingSpan traceJoinSpan = pendingSpans.getOrCreate(traceJoin, false);
    PendingSpan trace2Span = pendingSpans.getOrCreate(trace2, false);
    PendingSpan traceChildSpan = pendingSpans.getOrCreate(traceChild, false);

    assertThat(traceSpan.clock).isSameAs(traceChildSpan.clock);
    assertThat(traceSpan.clock).isSameAs(traceJoinSpan.clock);
    assertThat(traceSpan.clock).isNotSameAs(trace2Span.clock);
  }

  @Test
  public void getOrCreate_cachesReference() {
    PendingSpan span = pendingSpans.getOrCreate(context, false);
    assertThat(pendingSpans.getOrCreate(context, false)).isSameAs(span);
  }

  @Test
  public void getOrCreate_resolvesHashCodeCollisions() {
    // intentionally clash on hashCode, but not equals
    TraceContext context1 = context.toBuilder().spanId(1).build();
    TraceContext context2 = context.toBuilder().spanId(-2L).build();

    // sanity check
    assertThat(context1.hashCode()).isEqualTo(context2.hashCode());
    assertThat(context1).isNotEqualTo(context2);

    assertThat(pendingSpans.getOrCreate(context1, false)).isNotEqualTo(
      pendingSpans.getOrCreate(context2, false));
  }

  @Test
  public void getOrCreate_splitsSharedServerDataFromClient() {
    TraceContext context2 = context.toBuilder().shared(true).build();

    assertThat(pendingSpans.getOrCreate(context, false)).isNotEqualTo(
      pendingSpans.getOrCreate(context2, false));
  }

  @Test
  public void remove_clearsReference() {
    pendingSpans.getOrCreate(context, false);
    pendingSpans.remove(context);

    assertThat(pendingSpans.delegate).isEmpty();
    assertThat(pendingSpans.poll()).isNull();
  }

  @Test
  public void remove_doesntReport() {
    pendingSpans.getOrCreate(context, false);
    pendingSpans.remove(context);

    assertThat(spans).isEmpty();
  }

  @Test
  public void remove_okWhenDoesntExist() {
    pendingSpans.remove(context);
  }

  @Test
  public void remove_resolvesHashCodeCollisions() {
    // intentionally clash on hashCode, but not equals
    TraceContext context1 = context.toBuilder().spanId(1).build();
    TraceContext context2 = context.toBuilder().spanId(-2L).build();

    // sanity check
    assertThat(context1.hashCode()).isEqualTo(context2.hashCode());
    assertThat(context1).isNotEqualTo(context2);

    pendingSpans.getOrCreate(context1, false);
    pendingSpans.getOrCreate(context2, false);

    pendingSpans.remove(context1);

    assertThat(pendingSpans.delegate.keySet()).extracting(o -> ((Reference) o).get())
      .containsOnly(context2);
  }

  /** mainly ensures internals aren't dodgy on null */
  @Test
  public void remove_whenSomeReferencesAreCleared() {
    pendingSpans.getOrCreate(context, false);
    pretendGCHappened();
    pendingSpans.remove(context);

    assertThat(pendingSpans.delegate.keySet()).extracting(o -> ((Reference) o).get())
      .hasSize(1)
      .containsNull();
  }

  @Test
  public void getOrCreate_whenSomeReferencesAreCleared() {
    pendingSpans.getOrCreate(context, false);
    pretendGCHappened();
    pendingSpans.getOrCreate(context, false);

    // we'd expect two distinct entries.. the span would be reported twice, but merged zipkin-side
    assertThat(pendingSpans.delegate.keySet()).extracting(o -> ((Reference) o).get())
      .containsExactlyInAnyOrder(null, context);
  }

  /**
   * This is the key feature. Spans orphaned via GC are reported to zipkin on the next action.
   *
   * <p>This is a customized version of https://github.com/raphw/weak-lock-free/blob/master/src/test/java/com/blogspot/mydailyjava/weaklockfree/WeakConcurrentMapTest.java
   */
  @Test
  public void reportOrphanedSpans_afterGC() throws Exception {
    TraceContext context1 = context.toBuilder().traceId(1).spanId(1).build();
    PendingSpan span = pendingSpans.getOrCreate(context1, false);
    span.state.name("foo");
    span = null; // clear reference so GC occurs
    TraceContext context2 = context.toBuilder().traceId(2).spanId(2).build();
    pendingSpans.getOrCreate(context2, false);
    TraceContext context3 = context.toBuilder().traceId(3).spanId(3).build();
    pendingSpans.getOrCreate(context3, false);
    TraceContext context4 = context.toBuilder().traceId(4).spanId(4).build();
    pendingSpans.getOrCreate(context4, false);
    // ensure sampled local spans are not reported when orphaned unless they are also sampled remote
    TraceContext context5 =
      context.toBuilder().spanId(5).sampledLocal(true).sampled(false).build();
    pendingSpans.getOrCreate(context5, false);

    int initialClockVal = clock.get();

    // By clearing strong references in this test, we are left with the weak ones in the map
    context1 = context2 = context5 = null;
    blockOnGC();

    // After GC, we expect that the weak references of context1 and context2 to be cleared
    assertThat(pendingSpans.delegate.keySet()).extracting(o -> ((Reference) o).get())
      .containsExactlyInAnyOrder(null, null, context3, context4, null);

    pendingSpans.reportOrphanedSpans();

    // After reporting, we expect no the weak references of null
    assertThat(pendingSpans.delegate.keySet()).extracting(o -> ((Reference) o).get())
      .containsExactlyInAnyOrder(context3, context4);

    // We also expect only the sampled span containing data to have been reported
    assertThat(spans).hasSize(1);
    assertThat(spans.get(0).id()).isEqualTo("0000000000000001");
    assertThat(spans.get(0).name()).isEqualTo("foo"); // data was flushed
    assertThat(spans.get(0).annotations())
      .containsExactly(Annotation.create((initialClockVal + 1) * 1000, "brave.flush"));
  }

  @Test
  public void noop_afterGC() throws Exception {
    TraceContext context1 = context.toBuilder().spanId(1).build();
    pendingSpans.getOrCreate(context1, false);
    TraceContext context2 = context.toBuilder().spanId(2).build();
    pendingSpans.getOrCreate(context2, false);
    TraceContext context3 = context.toBuilder().spanId(3).build();
    pendingSpans.getOrCreate(context3, false);
    TraceContext context4 = context.toBuilder().spanId(4).build();
    pendingSpans.getOrCreate(context4, false);

    int initialClockVal = clock.get();

    pendingSpans.noop.set(true);

    // By clearing strong references in this test, we are left with the weak ones in the map
    context1 = context2 = null;
    blockOnGC();

    // After GC, we expect that the weak references of context1 and context2 to be cleared
    assertThat(pendingSpans.delegate.keySet()).extracting(o -> ((Reference) o).get())
      .containsExactlyInAnyOrder(null, null, context3, context4);

    pendingSpans.reportOrphanedSpans();

    // After reporting, we expect no the weak references of null
    assertThat(pendingSpans.delegate.keySet()).extracting(o -> ((Reference) o).get())
      .containsExactlyInAnyOrder(context3, context4);

    // since this is noop, we don't expect any spans to be reported
    assertThat(spans).isEmpty();

    // we also expect the clock to not have been called
    assertThat(clock.get()).isEqualTo(initialClockVal);
  }

  /** We ensure that the implicit caller of reportOrphanedSpans doesn't crash on report failure */
  @Test
  public void reportOrphanedSpans_whenReporterDies() throws Exception {
    init(new FinishedSpanHandler() {
      @Override public boolean handle(TraceContext context, MutableSpan span) {
        throw new RuntimeException();
      }
    });

    // We drop the reference to the context, which means the next GC should attempt to flush it
    pendingSpans.getOrCreate(context.toBuilder().build(), false);

    blockOnGC();

    // Sanity check that the referent trace context cleared due to GC
    assertThat(pendingSpans.delegate.keySet()).extracting(o -> ((Reference) o).get())
      .hasSize(1)
      .containsNull();

    // The innocent caller isn't killed due to the exception in implicitly reporting GC'd spans
    pendingSpans.remove(context);

    // However, the reference queue has been cleared.
    assertThat(pendingSpans.delegate.keySet())
      .isEmpty();
  }

  /** Debugging should show what the spans are, as well any references pending clear. */
  @Test
  public void toString_saysWhatReferentsAre() {
    assertThat(pendingSpans.toString())
      .isEqualTo("PendingSpans[]");

    pendingSpans.getOrCreate(context, false);

    assertThat(pendingSpans.toString())
      .isEqualTo("PendingSpans[WeakReference(" + context + ")]");

    pretendGCHappened();

    assertThat(pendingSpans.toString())
      .isEqualTo("PendingSpans[ClearedReference()]");
  }

  @Test
  public void realKey_equalToItself() {
    PendingSpans.RealKey key = new PendingSpans.RealKey(context, pendingSpans);
    assertThat(key).isEqualTo(key);
    key.clear();
    assertThat(key).isEqualTo(key);
  }

  @Test
  public void orphanContext_dropsExtra() throws Exception {
    TraceContext context1 = context.toBuilder().extra(asList(1, true)).build();

    TraceContext[] handledContext = {null};
    init(new FinishedSpanHandler() {
      @Override public boolean handle(TraceContext context, MutableSpan span) {
        handledContext[0] = context;
        return true;
      }
    });

    // We drop the reference to the context, which means the next GC should attempt to flush it
    pendingSpans.getOrCreate(context1.toBuilder().build(), false).state().tag("foo", "bar");

    blockOnGC();
    pendingSpans.reportOrphanedSpans();

    assertThat(handledContext[0]).isEqualTo(context1); // ID comparision is the same
    assertThat(handledContext[0].extra()).isEmpty(); // No extra fields are retained
  }

  @Test
  public void orphanContext_includesAllFlags() throws Exception {
    TraceContext context1 = context.toBuilder().sampled(null).sampledLocal(true).shared(true).build();

    TraceContext[] handledContext = {null};
    init(new FinishedSpanHandler() {
      @Override public boolean handle(TraceContext context, MutableSpan span) {
        handledContext[0] = context;
        return true;
      }
    });

    // We drop the reference to the context, which means the next GC should attempt to flush it
    pendingSpans.getOrCreate(context1.toBuilder().build(), false).state().tag("foo", "bar");

    blockOnGC();
    pendingSpans.reportOrphanedSpans();

    assertThat(InternalPropagation.instance.flags(handledContext[0]))
      .isEqualTo(InternalPropagation.instance.flags(context1)); // no flags lost
  }

  @Test
  public void lookupKey_hashCode() {
    TraceContext context1 = context;
    TraceContext context2 = context.toBuilder().shared(true).build();

    assertThat(PendingSpans.LookupKey.generateHashCode(
      context1.traceIdHigh(), context1.traceId(), context1.spanId(), context1.shared()
    )).isEqualTo(context1.hashCode());

    assertThat(PendingSpans.LookupKey.generateHashCode(
      context2.traceIdHigh(), context2.traceId(), context2.spanId(), context2.shared()
    )).isEqualTo(context2.hashCode());
  }

  @Test
  public void realKey_equalToEquivalent() {
    PendingSpans.RealKey key = new PendingSpans.RealKey(context, pendingSpans);
    PendingSpans.RealKey key2 = new PendingSpans.RealKey(context, pendingSpans);
    assertThat(key).isEqualTo(key2);
    key.clear();
    assertThat(key).isNotEqualTo(key2);
    key2.clear();
    assertThat(key).isEqualTo(key2);
  }

  @Test
  public void lookupKey_equalToRealKey() {
    PendingSpans.LookupKey lookupKey = new PendingSpans.LookupKey();
    lookupKey.set(context);
    PendingSpans.RealKey key = new PendingSpans.RealKey(context, pendingSpans);
    assertThat(lookupKey.equals(key)).isTrue();
    key.clear();
    assertThat(lookupKey.equals(key)).isFalse();
  }

  @Test
  public void lookupKey_equalToRealKey_shared() {
    context = context.toBuilder().shared(true).build();
    PendingSpans.LookupKey lookupKey = new PendingSpans.LookupKey();
    lookupKey.set(context);
    PendingSpans.RealKey key = new PendingSpans.RealKey(context, pendingSpans);
    assertThat(lookupKey.equals(key)).isTrue();
    key = new PendingSpans.RealKey(context.toBuilder().shared(false).build(), pendingSpans);
    assertThat(lookupKey.equals(key)).isFalse();
  }

  /** In reality, this clears a reference even if it is strongly held by the test! */
  void pretendGCHappened() {
    ((PendingSpans.RealKey) pendingSpans.delegate.keySet().iterator().next()).clear();
  }

  static void blockOnGC() throws InterruptedException {
    System.gc();
    Thread.sleep(200L);
  }
}

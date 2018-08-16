package brave.internal.recorder;

import brave.propagation.TraceContext;
import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import zipkin2.Annotation;
import zipkin2.Endpoint;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;

public class PendingSpansTest {
  Endpoint endpoint = Endpoint.newBuilder().serviceName("PendingSpansTest").build();
  List<zipkin2.Span> spans = new ArrayList<>();
  TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true).build();
  AtomicInteger clock = new AtomicInteger();
  PendingSpans map = new PendingSpans(
      endpoint, () -> clock.incrementAndGet() * 1000L, spans::add, new AtomicBoolean(false));

  @Test
  public void getOrCreate_lazyCreatesASpan() {
    PendingSpan span = map.getOrCreate(context, false);

    assertThat(span).isNotNull();
  }

  /** Ensure we use the same clock for traces that started in-process */
  @Test
  public void getOrCreate_reusesClockFromParent() {
    TraceContext trace = TraceContext.newBuilder().traceId(1L).spanId(2L).build();
    TraceContext traceJoin = trace.toBuilder().shared(true).build();
    TraceContext trace2 = TraceContext.newBuilder().traceId(2L).spanId(2L).build();
    TraceContext traceChild = TraceContext.newBuilder().traceId(1L).parentId(2L).spanId(3L).build();

    PendingSpan traceSpan = map.getOrCreate(trace, false);
    PendingSpan traceJoinSpan = map.getOrCreate(traceJoin, false);
    PendingSpan trace2Span = map.getOrCreate(trace2, false);
    PendingSpan traceChildSpan = map.getOrCreate(traceChild, false);

    assertThat(traceSpan.clock).isSameAs(traceChildSpan.clock);
    assertThat(traceSpan.clock).isSameAs(traceJoinSpan.clock);
    assertThat(traceSpan.clock).isNotSameAs(trace2Span.clock);
  }

  @Test
  public void getOrCreate_cachesReference() {
    PendingSpan span = map.getOrCreate(context, false);
    assertThat(map.getOrCreate(context, false)).isSameAs(span);
  }

  @Test
  public void getOrCreate_resolvesHashCodeCollisions() {
    // intentionally clash on hashCode, but not equals
    TraceContext context1 = context.toBuilder().spanId(1).build();
    TraceContext context2 = context.toBuilder().spanId(-2L).build();

    // sanity check
    assertThat(context1.hashCode()).isEqualTo(context2.hashCode());
    assertThat(context1).isNotEqualTo(context2);

    assertThat(map.getOrCreate(context1, false)).isNotEqualTo(map.getOrCreate(context2, false));
  }

  @Test
  public void getOrCreate_splitsSharedServerDataFromClient() {
    TraceContext context2 = context.toBuilder().shared(true).build();

    assertThat(map.getOrCreate(context, false)).isNotEqualTo(map.getOrCreate(context2, false));
  }

  @Test
  public void remove_clearsReference() {
    map.getOrCreate(context, false);
    map.remove(context);

    assertThat(map.delegate).isEmpty();
    assertThat(map.poll()).isNull();
  }

  @Test
  public void remove_doesntReport() {
    map.getOrCreate(context, false);
    map.remove(context);

    assertThat(spans).isEmpty();
  }

  @Test
  public void remove_okWhenDoesntExist() {
    map.remove(context);
  }

  @Test
  public void remove_resolvesHashCodeCollisions() {
    // intentionally clash on hashCode, but not equals
    TraceContext context1 = context.toBuilder().spanId(1).build();
    TraceContext context2 = context.toBuilder().spanId(-2L).build();

    // sanity check
    assertThat(context1.hashCode()).isEqualTo(context2.hashCode());
    assertThat(context1).isNotEqualTo(context2);

    map.getOrCreate(context1, false);
    map.getOrCreate(context2, false);

    map.remove(context1);

    assertThat(map.delegate.keySet()).extracting(o -> ((Reference) o).get())
        .containsOnly(context2);
  }

  /** mainly ensures internals aren't dodgy on null */
  @Test
  public void remove_whenSomeReferencesAreCleared() {
    map.getOrCreate(context, false);
    pretendGCHappened();
    map.remove(context);

    assertThat(map.delegate.keySet()).extracting(o -> ((Reference) o).get())
        .hasSize(1)
        .containsNull();
  }

  @Test
  public void getOrCreate_whenSomeReferencesAreCleared() {
    map.getOrCreate(context, false);
    pretendGCHappened();
    map.getOrCreate(context, false);

    // we'd expect two distinct entries.. the span would be reported twice, but merged zipkin-side
    assertThat(map.delegate.keySet()).extracting(o -> ((Reference) o).get())
        .containsExactlyInAnyOrder(null, context);
  }

  /**
   * This is the key feature. Spans orphaned via GC are reported to zipkin on the next action.
   *
   * <p>This is a customized version of https://github.com/raphw/weak-lock-free/blob/master/src/test/java/com/blogspot/mydailyjava/weaklockfree/WeakConcurrentMapTest.java
   */
  @Test
  public void reportOrphanedSpans_afterGC() {
    TraceContext context1 = context.toBuilder().spanId(1).build();
    map.getOrCreate(context1, false);
    TraceContext context2 = context.toBuilder().spanId(2).build();
    map.getOrCreate(context2, false);
    TraceContext context3 = context.toBuilder().spanId(3).build();
    map.getOrCreate(context3, false);
    TraceContext context4 = context.toBuilder().spanId(4).build();
    map.getOrCreate(context4, false);

    int initialClockVal = clock.get();

    // By clearing strong references in this test, we are left with the weak ones in the map
    context1 = context2 = null;
    blockOnGC();

    // After GC, we expect that the weak references of context1 and context2 to be cleared
    assertThat(map.delegate.keySet()).extracting(o -> ((Reference) o).get())
        .containsExactlyInAnyOrder(null, null, context3, context4);

    map.reportOrphanedSpans();

    // After reporting, we expect no the weak references of null
    assertThat(map.delegate.keySet()).extracting(o -> ((Reference) o).get())
        .containsExactlyInAnyOrder(context3, context4);

    // We also expect the spans to have been reported
    assertThat(spans).flatExtracting(Span::annotations).extracting(Annotation::value)
        .containsExactly("brave.flush", "brave.flush");

    // We also expect the spans reported to have the endpoint of the tracer
    assertThat(spans).extracting(Span::localEndpoint)
        .containsExactly(endpoint, endpoint);

    // we also expect the clock to have been called only once
    assertThat(spans).flatExtracting(Span::annotations).extracting(Annotation::timestamp)
        .allSatisfy(t -> assertThat(t).isEqualTo((initialClockVal + 1) * 1000));
  }

  @Test
  public void noop_afterGC() {
    TraceContext context1 = context.toBuilder().spanId(1).build();
    map.getOrCreate(context1, false);
    TraceContext context2 = context.toBuilder().spanId(2).build();
    map.getOrCreate(context2, false);
    TraceContext context3 = context.toBuilder().spanId(3).build();
    map.getOrCreate(context3, false);
    TraceContext context4 = context.toBuilder().spanId(4).build();
    map.getOrCreate(context4, false);

    int initialClockVal = clock.get();

    map.noop.set(true);

    // By clearing strong references in this test, we are left with the weak ones in the map
    context1 = context2 = null;
    blockOnGC();

    // After GC, we expect that the weak references of context1 and context2 to be cleared
    assertThat(map.delegate.keySet()).extracting(o -> ((Reference) o).get())
        .containsExactlyInAnyOrder(null, null, context3, context4);

    map.reportOrphanedSpans();

    // After reporting, we expect no the weak references of null
    assertThat(map.delegate.keySet()).extracting(o -> ((Reference) o).get())
        .containsExactlyInAnyOrder(context3, context4);

    // since this is noop, we don't expect any spans to be reported
    assertThat(spans).isEmpty();

    // we also expect the clock to not have been called
    assertThat(clock.get()).isEqualTo(initialClockVal);
  }

  /** We ensure that the implicit caller of reportOrphanedSpans doesn't crash on report failure */
  @Test
  public void reportOrphanedSpans_whenReporterDies() {
    PendingSpans map = new PendingSpans(endpoint, () -> 0, span ->
    {
      throw new RuntimeException("die!");
    }, new AtomicBoolean(true));

    // We drop the reference to the context, which means the next GC should attempt to flush it
    map.getOrCreate(context.toBuilder().build(), false);

    blockOnGC();

    // Sanity check that the referent trace context cleared due to GC
    assertThat(map.delegate.keySet()).extracting(o -> ((Reference) o).get())
        .hasSize(1)
        .containsNull();

    // The innocent caller isn't killed due to the exception in implicitly reporting GC'd spans
    map.remove(context);

    // However, the reference queue has been cleared.
    assertThat(map.delegate.keySet())
        .isEmpty();
  }

  /** Debugging should show what the spans are, as well any references pending clear. */
  @Test
  public void toString_saysWhatReferentsAre() {
    assertThat(map.toString())
        .isEqualTo("PendingSpans[]");

    map.getOrCreate(context, false);

    assertThat(map.toString())
        .isEqualTo("PendingSpans[WeakReference(" + context + ")]");

    pretendGCHappened();

    assertThat(map.toString())
        .isEqualTo("PendingSpans[ClearedReference()]");
  }

  @Test
  public void realKey_equalToItself() {
    PendingSpans.RealKey key = new PendingSpans.RealKey(context, map);
    assertThat(key).isEqualTo(key);
    key.clear();
    assertThat(key).isEqualTo(key);
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
    PendingSpans.RealKey key = new PendingSpans.RealKey(context, map);
    PendingSpans.RealKey key2 = new PendingSpans.RealKey(context, map);
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
    PendingSpans.RealKey key = new PendingSpans.RealKey(context, map);
    assertThat(lookupKey.equals(key)).isTrue();
    key.clear();
    assertThat(lookupKey.equals(key)).isFalse();
  }

  @Test
  public void lookupKey_equalToRealKey_shared() {
    context = context.toBuilder().shared(true).build();
    PendingSpans.LookupKey lookupKey = new PendingSpans.LookupKey();
    lookupKey.set(context);
    PendingSpans.RealKey key = new PendingSpans.RealKey(context, map);
    assertThat(lookupKey.equals(key)).isTrue();
    key = new PendingSpans.RealKey(context.toBuilder().shared(false).build(), map);
    assertThat(lookupKey.equals(key)).isFalse();
  }

  /** In reality, this clears a reference even if it is strongly held by the test! */
  void pretendGCHappened() {
    ((PendingSpans.RealKey) map.delegate.keySet().iterator().next()).clear();
  }

  static void blockOnGC() {
    System.gc();
    try {
      Thread.sleep(200L);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }
}

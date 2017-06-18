package brave.internal.recorder;

import brave.Tracing;
import brave.internal.Platform;
import brave.propagation.TraceContext;
import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import zipkin.Endpoint;

import static org.assertj.core.api.Assertions.assertThat;

public class MutableSpanMapTest {
  Endpoint localEndpoint = Platform.get().localEndpoint();
  List<zipkin.Span> spans = new ArrayList();
  TraceContext context = Tracing.newBuilder().build().tracer().newTrace().context();
  MutableSpanMap map =
      new MutableSpanMap(localEndpoint, () -> 0L, spans::add, new AtomicBoolean(false));

  @Test
  public void getOrCreate_lazyCreatesASpan() throws Exception {
    MutableSpan span = map.getOrCreate(context);

    assertThat(span).isNotNull();
    assertThat(span.localEndpoint).isEqualTo(localEndpoint);
  }

  @Test
  public void getOrCreate_cachesReference() throws Exception {
    MutableSpan span = map.getOrCreate(context);
    assertThat(map.getOrCreate(context)).isSameAs(span);
  }

  @Test
  public void getOrCreate_resolvesHashCodeCollisions() throws Exception {
    // intentionally clash on hashCode, but not equals
    TraceContext context1 = context.toBuilder().spanId(1).build();
    TraceContext context2 = context.toBuilder().spanId(-2L).build();

    // sanity check
    assertThat(context1.hashCode()).isEqualTo(context2.hashCode());
    assertThat(context1).isNotEqualTo(context2);

    assertThat(map.getOrCreate(context1)).isNotEqualTo(map.getOrCreate(context2));
  }

  @Test
  public void remove_clearsReference() throws Exception {
    map.getOrCreate(context);
    map.remove(context);

    assertThat(map.delegate).isEmpty();
    assertThat(map.poll()).isNull();
  }

  @Test
  public void remove_doesntReport() throws Exception {
    map.getOrCreate(context);
    map.remove(context);

    assertThat(spans).isEmpty();
  }

  @Test
  public void remove_okWhenDoesntExist() throws Exception {
    MutableSpan span = map.remove(context);
    assertThat(span).isNull();
  }

  @Test
  public void remove_resolvesHashCodeCollisions() throws Exception {
    // intentionally clash on hashCode, but not equals
    TraceContext context1 = context.toBuilder().spanId(1).build();
    TraceContext context2 = context.toBuilder().spanId(-2L).build();

    // sanity check
    assertThat(context1.hashCode()).isEqualTo(context2.hashCode());
    assertThat(context1).isNotEqualTo(context2);

    map.getOrCreate(context1);
    map.getOrCreate(context2);

    map.remove(context1);

    assertThat(map.delegate.keySet()).extracting(o -> ((Reference) o).get())
        .containsOnly(context2);
  }

  /** mainly ensures internals aren't dodgy on null */
  @Test
  public void remove_whenSomeReferencesAreCleared() throws Exception {
    map.getOrCreate(context);
    pretendGCHappened();
    map.remove(context);

    assertThat(map.delegate.keySet()).extracting(o -> ((Reference) o).get())
        .hasSize(1)
        .containsNull();
  }

  @Test
  public void getOrCreate_whenSomeReferencesAreCleared() throws Exception {
    map.getOrCreate(context);
    pretendGCHappened();
    map.getOrCreate(context);

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
  public void reportOrphanedSpans_afterGC() throws Exception {
    TraceContext context1 = context.toBuilder().spanId(1).build();
    map.getOrCreate(context1);
    TraceContext context2 = context.toBuilder().spanId(2).build();
    map.getOrCreate(context2);
    TraceContext context3 = context.toBuilder().spanId(3).build();
    map.getOrCreate(context3);
    TraceContext context4 = context.toBuilder().spanId(4).build();
    map.getOrCreate(context4);

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
    assertThat(spans).flatExtracting(s -> s.annotations).extracting(a -> a.value)
        .containsExactly("brave.flush", "brave.flush");
  }

  @Test
  public void noop_afterGC() throws Exception {
    TraceContext context1 = context.toBuilder().spanId(1).build();
    map.getOrCreate(context1);
    TraceContext context2 = context.toBuilder().spanId(2).build();
    map.getOrCreate(context2);
    TraceContext context3 = context.toBuilder().spanId(3).build();
    map.getOrCreate(context3);
    TraceContext context4 = context.toBuilder().spanId(4).build();
    map.getOrCreate(context4);

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
  }

  /** We ensure that the implicit caller of reportOrphanedSpans doesn't crash on report failure */
  @Test
  public void reportOrphanedSpans_whenReporterDies() throws Exception {
    MutableSpanMap map = new MutableSpanMap(localEndpoint, () -> 0, span ->
    {
      throw new RuntimeException("die!");
    }, new AtomicBoolean(true));

    // We drop the reference to the context, which means the next GC should attempt to flush it
    map.getOrCreate(context.toBuilder().build());

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
  public void toString_saysWhatReferentsAre() throws Exception {
    assertThat(map.toString())
        .isEqualTo("MutableSpanMap[]");

    map.getOrCreate(context);

    assertThat(map.toString())
        .isEqualTo("MutableSpanMap[WeakReference(" + context + ")]");

    pretendGCHappened();

    assertThat(map.toString())
        .isEqualTo("MutableSpanMap[ClearedReference()]");
  }

  @Test
  public void realKey_equalToItself() {
    MutableSpanMap.RealKey key = new MutableSpanMap.RealKey(context, map);
    assertThat(key).isEqualTo(key);
    key.clear();
    assertThat(key).isEqualTo(key);
  }

  @Test
  public void realKey_equalToEquivalent() {
    MutableSpanMap.RealKey key = new MutableSpanMap.RealKey(context, map);
    MutableSpanMap.RealKey key2 = new MutableSpanMap.RealKey(context, map);
    assertThat(key).isEqualTo(key2);
    key.clear();
    assertThat(key).isNotEqualTo(key2);
    key2.clear();
    assertThat(key).isEqualTo(key2);
  }

  /** In reality, this clears a reference even if it is strongly held by the test! */
  void pretendGCHappened() {
    ((MutableSpanMap.RealKey) map.delegate.keySet().iterator().next()).clear();
  }

  static void blockOnGC() throws InterruptedException {
    System.gc();
    Thread.sleep(200L);
  }
}

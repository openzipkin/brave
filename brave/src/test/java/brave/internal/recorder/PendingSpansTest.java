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

import brave.GarbageCollectors;
import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.internal.InternalPropagation;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;
import zipkin2.Span;

import static brave.internal.InternalPropagation.FLAG_LOCAL_ROOT;
import static brave.internal.InternalPropagation.FLAG_SAMPLED;
import static brave.internal.InternalPropagation.FLAG_SAMPLED_SET;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class PendingSpansTest {
  static {
    SamplingFlags.NOT_SAMPLED.toString(); // ensure InternalPropagation is wired for tests
  }

  List<zipkin2.Span> spans = new ArrayList<>();
  // PendingSpans should always be passed a trace context instantiated by the Tracer. This fakes
  // a local root span, so that we don't have to depend on the Tracer to run these tests.
  TraceContext context = InternalPropagation.instance.newTraceContext(
    FLAG_SAMPLED_SET | FLAG_SAMPLED | FLAG_LOCAL_ROOT,
    0L,
    1L,
    2L,
    0L,
    1L,
    Collections.emptyList()
  );
  AtomicInteger clock = new AtomicInteger();
  PendingSpans pendingSpans;

  @Before public void init() {
    init(new SpanHandler() {
      @Override public boolean end(TraceContext ctx, MutableSpan span, Cause cause) {
        if (!Boolean.TRUE.equals(ctx.sampled())) return true;

        Span.Builder b = Span.newBuilder().traceId(ctx.traceIdString()).id(ctx.traceIdString());
        b.name(span.name());
        span.forEachAnnotation(Span.Builder::addAnnotation, b);
        spans.add(b.build());
        return true;
      }
    });
  }

  void init(SpanHandler handler) {
    MutableSpan defaultSpan = new MutableSpan();
    defaultSpan.localServiceName("favistar");
    defaultSpan.localIp("1.2.3.4");
    pendingSpans = new PendingSpans(defaultSpan, () -> clock.incrementAndGet() * 1000L, handler,
      new AtomicBoolean());
  }

  @Test
  public void getOrCreate_lazyCreatesASpan() {
    PendingSpan span = pendingSpans.getOrCreate(null, context, false);

    assertThat(span).isNotNull();
  }

  /** Ensure we use the same clock for traces that started in-process */
  @Test
  public void getOrCreate_reusesClockFromParent() {
    TraceContext trace = context;
    TraceContext traceJoin = trace.toBuilder().shared(true).build();
    TraceContext trace2 = context.toBuilder().traceId(2L).build();
    TraceContext traceChild =
      TraceContext.newBuilder().traceId(1L).parentId(trace.spanId()).spanId(3L).build();

    PendingSpan traceSpan = pendingSpans.getOrCreate(null, trace, false);
    PendingSpan traceJoinSpan = pendingSpans.getOrCreate(trace, traceJoin, false);
    PendingSpan trace2Span = pendingSpans.getOrCreate(null, trace2, false);
    PendingSpan traceChildSpan = pendingSpans.getOrCreate(trace, traceChild, false);

    assertThat(traceSpan.clock).isSameAs(traceChildSpan.clock);
    assertThat(traceSpan.clock).isSameAs(traceJoinSpan.clock);
    assertThat(traceSpan.clock).isNotSameAs(trace2Span.clock);
  }

  @Test
  public void getOrCreate_cachesReference() {
    PendingSpan span = pendingSpans.getOrCreate(null, context, false);
    assertThat(pendingSpans.getOrCreate(null, context, false)).isSameAs(span);
  }

  @Test
  public void getOrCreate_splitsSharedServerDataFromClient() {
    TraceContext context2 = context.toBuilder().shared(true).build();

    assertThat(pendingSpans.getOrCreate(null, context, false)).isNotEqualTo(
      pendingSpans.getOrCreate(null, context2, false));
  }

  @Test
  public void remove_doesntReport() {
    pendingSpans.getOrCreate(null, context, false);
    pendingSpans.remove(context);

    assertThat(spans).isEmpty();
  }

  /**
   * This is the key feature. Spans orphaned via GC are reported to zipkin on the next action.
   *
   * <p>This is a customized version of https://github.com/raphw/weak-lock-free/blob/master/src/test/java/com/blogspot/mydailyjava/weaklockfree/WeakConcurrentMapTest.java
   */
  @Test
  public void reportOrphanedSpans_afterGC() {
    TraceContext context1 = context.toBuilder().traceId(1).spanId(1).build();
    PendingSpan span = pendingSpans.getOrCreate(null, context1, false);
    span.span.name("foo");
    span = null; // clear reference so GC occurs
    TraceContext context2 = context.toBuilder().traceId(2).spanId(2).build();
    pendingSpans.getOrCreate(null, context2, false);
    TraceContext context3 = context.toBuilder().traceId(3).spanId(3).build();
    pendingSpans.getOrCreate(null, context3, false);
    TraceContext context4 = context.toBuilder().traceId(4).spanId(4).build();
    pendingSpans.getOrCreate(null, context4, false);
    // ensure sampled local spans are not reported when orphaned unless they are also sampled remote
    TraceContext context5 =
      context.toBuilder().spanId(5).sampledLocal(true).sampled(false).build();
    pendingSpans.getOrCreate(null, context5, false);

    // By clearing strong references in this test, we are left with the weak ones in the map
    context1 = context2 = context5 = null;
    GarbageCollectors.blockOnGC();

    pendingSpans.expungeStaleEntries();

    assertThat(spans).hasSize(2);
    // orphaned without data
    assertThat(spans.get(0).id()).isEqualTo("0000000000000002");
    // orphaned with data
    assertThat(spans.get(1).id()).isEqualTo("0000000000000001");
    assertThat(spans.get(1).name()).isEqualTo("foo"); // data was flushed
  }

  @Test
  public void noop_afterGC() {
    TraceContext context1 = context.toBuilder().spanId(1).build();
    pendingSpans.getOrCreate(null, context1, false);
    TraceContext context2 = context.toBuilder().spanId(2).build();
    pendingSpans.getOrCreate(null, context2, false);
    TraceContext context3 = context.toBuilder().spanId(3).build();
    pendingSpans.getOrCreate(null, context3, false);
    TraceContext context4 = context.toBuilder().spanId(4).build();
    pendingSpans.getOrCreate(null, context4, false);

    int initialClockVal = clock.get();

    pendingSpans.noop.set(true);

    // By clearing strong references in this test, we are left with the weak ones in the map
    context1 = context2 = null;

    pendingSpans.expungeStaleEntries();

    // since this is noop, we don't expect any spans to be reported
    assertThat(spans).isEmpty();

    // we also expect the clock to not have been called
    assertThat(clock.get()).isEqualTo(initialClockVal);
  }

  @Test
  public void orphanContext_dropsExtra() {
    TraceContext context1 = context.toBuilder().extra(asList(1, true)).build();

    TraceContext[] handledContext = {null};
    init(new SpanHandler() {
      @Override public boolean end(TraceContext context, MutableSpan span, Cause cause) {
        handledContext[0] = context;
        return true;
      }
    });

    TraceContext context = this.context.toBuilder().build();
    pendingSpans.getOrCreate(null, context, false).state().tag("foo", "bar");
    // We drop the reference to the context, which means the next GC should attempt to flush it
    context = null;

    GarbageCollectors.blockOnGC();
    pendingSpans.expungeStaleEntries();

    assertThat(handledContext[0]).isEqualTo(context1); // ID comparision is the same
    assertThat(handledContext[0].extra()).isEmpty(); // No context decorations are retained
  }

  @Test
  public void orphanContext_includesAllFlags() {
    TraceContext context1 =
      context.toBuilder().sampled(null).sampledLocal(true).shared(true).build();

    TraceContext[] handledContext = {null};
    init(new SpanHandler() {
      @Override public boolean end(TraceContext context, MutableSpan span, Cause cause) {
        handledContext[0] = context;
        return true;
      }
    });

    TraceContext context = context1.toBuilder().build();
    pendingSpans.getOrCreate(null, context, false).state().tag("foo", "bar");
    // We drop the reference to the context, which means the next GC should attempt to flush it
    context = null;

    GarbageCollectors.blockOnGC();
    pendingSpans.expungeStaleEntries();

    assertThat(InternalPropagation.instance.flags(handledContext[0]))
      .isEqualTo(InternalPropagation.instance.flags(context1)); // no flags lost
  }
}

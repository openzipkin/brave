package brave;

import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import brave.sampler.Sampler;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import zipkin.Endpoint;

import static org.assertj.core.api.Assertions.assertThat;

public class TracerTest {
  Tracer tracer = Tracing.newBuilder().build().tracer();

  @Test public void sampler() {
    Sampler sampler = new Sampler() {
      @Override public boolean isSampled(long traceId) {
        return false;
      }
    };

    tracer = Tracing.newBuilder().sampler(sampler).build().tracer();

    assertThat(tracer.sampler)
        .isSameAs(sampler);
  }

  @Test public void localServiceName() {
    tracer = Tracing.newBuilder().localServiceName("my-foo").build().tracer();

    assertThat(tracer.localEndpoint.serviceName)
        .isEqualTo("my-foo");
  }

  @Test public void localServiceName_defaultIsUnknown() {
    assertThat(tracer.localEndpoint.serviceName)
        .isEqualTo("unknown");
  }

  @Test public void localServiceName_ignoredWhenGivenLocalEndpoint() {
    Endpoint localEndpoint = Endpoint.create("my-bar", 127 << 24 | 1);
    tracer = Tracing.newBuilder().localServiceName("my-foo")
        .localEndpoint(localEndpoint).build().tracer();

    assertThat(tracer.localEndpoint)
        .isSameAs(localEndpoint);
  }

  @Test public void clock() {
    Clock clock = () -> 0L;
    tracer = Tracing.newBuilder().clock(clock).build().tracer();

    assertThat(tracer.clock())
        .isSameAs(clock);
  }

  @Test public void newTrace_isRootSpan() {
    assertThat(tracer.newTrace())
        .satisfies(s -> assertThat(s.context().parentId()).isNull())
        .isInstanceOf(RealSpan.class);
  }

  @Test public void newTrace_traceId128Bit() {
    tracer = Tracing.newBuilder().traceId128Bit(true).build().tracer();

    assertThat(tracer.newTrace().context().traceIdHigh())
        .isNotZero();
  }

  @Test public void newTrace_unsampled_tracer() {
    tracer = Tracing.newBuilder().sampler(Sampler.NEVER_SAMPLE).build().tracer();

    assertThat(tracer.newTrace())
        .isInstanceOf(NoopSpan.class);
  }

  @Test public void newTrace_sampled_flag() {
    assertThat(tracer.newTrace(SamplingFlags.SAMPLED))
        .isInstanceOf(RealSpan.class);
  }

  @Test public void newTrace_noop_on_sampled_flag() {
    tracer.noop.set(true);

    assertThat(tracer.newTrace(SamplingFlags.SAMPLED))
        .isInstanceOf(NoopSpan.class);
  }

  @Test public void newTrace_debug_flag() {
    List<zipkin.Span> spans = new ArrayList<>();
    tracer = Tracing.newBuilder().reporter(spans::add).build().tracer();

    Span root = tracer.newTrace(SamplingFlags.DEBUG).start();
    root.finish();

    assertThat(spans).extracting(s -> s.debug)
        .containsExactly(true);
  }

  @Test public void newTrace_noop_on_debug_flag() {
    tracer.noop.set(true);

    assertThat(tracer.newTrace(SamplingFlags.DEBUG))
        .isInstanceOf(NoopSpan.class);
  }

  @Test public void newTrace_notsampled_flag() {
    assertThat(tracer.newTrace(SamplingFlags.NOT_SAMPLED))
        .isInstanceOf(NoopSpan.class);
  }

  /** When we join a sampled request, we are sharing the same trace identifiers. */
  @Test public void join_setsShared() {
    TraceContext fromIncomingRequest = tracer.newTrace().context();

    assertThat(tracer.joinSpan(fromIncomingRequest).context())
        .isEqualTo(fromIncomingRequest.toBuilder().shared(true).build());
  }

  @Test public void join_noop() {
    TraceContext fromIncomingRequest = tracer.newTrace().context();

    tracer.noop.set(true);

    assertThat(tracer.joinSpan(fromIncomingRequest))
        .isInstanceOf(NoopSpan.class);
  }

  @Test public void join_ensuresSampling() {
    TraceContext notYetSampled =
        tracer.newTrace().context().toBuilder().sampled(null).build();

    assertThat(tracer.joinSpan(notYetSampled).context())
        .isEqualTo(notYetSampled.toBuilder().sampled(true).build());
  }

  @Test public void toSpan() {
    TraceContext context = tracer.newTrace().context();

    assertThat(tracer.toSpan(context))
        .isInstanceOf(RealSpan.class)
        .extracting(Span::context)
        .containsExactly(context);
  }

  @Test public void toSpan_noop() {
    TraceContext context = tracer.newTrace().context();

    tracer.noop.set(true);

    assertThat(tracer.toSpan(context))
        .isInstanceOf(NoopSpan.class);
  }

  @Test public void toSpan_unsampledIsNoop() {
    TraceContext unsampled =
        tracer.newTrace().context().toBuilder().sampled(false).build();

    assertThat(tracer.toSpan(unsampled))
        .isInstanceOf(NoopSpan.class);
  }

  @Test public void newChild() {
    TraceContext parent = tracer.newTrace().context();

    assertThat(tracer.newChild(parent))
        .satisfies(c -> {
          assertThat(c.context().traceIdString()).isEqualTo(parent.traceIdString());
          assertThat(c.context().parentId()).isEqualTo(parent.spanId());
        })
        .isInstanceOf(RealSpan.class);
  }

  @Test public void newChild_noop() {
    TraceContext parent = tracer.newTrace().context();

    tracer.noop.set(true);

    assertThat(tracer.newChild(parent))
        .isInstanceOf(NoopSpan.class);
  }

  @Test public void newChild_unsampledIsNoop() {
    TraceContext unsampled =
        tracer.newTrace().context().toBuilder().sampled(false).build();

    assertThat(tracer.newChild(unsampled))
        .isInstanceOf(NoopSpan.class);
  }

  @Test public void currentSpan_defaultsToNull() {
    assertThat(tracer.currentSpan()).isNull();
  }

  @Test public void nextSpan_defaultsToMakeNewTrace() {
    assertThat(tracer.nextSpan().context().parentId()).isNull();
  }

  @Test public void nextSpan_makesChildOfCurrent() {
    Span parent = tracer.newTrace();

    try (Tracer.SpanInScope ws = tracer.withSpanInScope(parent)) {
      assertThat(tracer.nextSpan().context().parentId())
          .isEqualTo(parent.context().spanId());
    }
  }

  @Test public void withSpanInScope() {
    Span current = tracer.newTrace();

    try (Tracer.SpanInScope ws = tracer.withSpanInScope(current)) {
      assertThat(tracer.currentSpan())
          .isEqualTo(current);
    }

    // context was cleared
    assertThat(tracer.currentSpan()).isNull();
  }

  @Test public void withSpanInScope_nested() {
    Span parent = tracer.newTrace();

    try (Tracer.SpanInScope wsParent = tracer.withSpanInScope(parent)) {

      Span child = tracer.newChild(parent.context());
      try (Tracer.SpanInScope wsChild = tracer.withSpanInScope(child)) {
        assertThat(tracer.currentSpan())
            .isEqualTo(child);
      }

      // old parent reverted
      assertThat(tracer.currentSpan())
          .isEqualTo(parent);
    }
  }

  @Test public void withSpanInScope_clear() {
    Span parent = tracer.newTrace();

    try (Tracer.SpanInScope wsParent = tracer.withSpanInScope(parent)) {
      try (Tracer.SpanInScope clearScope = tracer.withSpanInScope(null)) {
        assertThat(tracer.currentSpan())
            .isNull();
      }

      // old parent reverted
      assertThat(tracer.currentSpan())
          .isEqualTo(parent);
    }
  }
}

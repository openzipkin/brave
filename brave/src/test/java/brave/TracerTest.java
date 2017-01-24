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
  Tracer tracer = Tracer.newBuilder().build();

  @Test public void sampler() {
    Sampler sampler = new Sampler() {
      @Override public boolean isSampled(long traceId) {
        return false;
      }
    };

    tracer = Tracer.newBuilder().sampler(sampler).build();

    assertThat(tracer.sampler)
        .isSameAs(sampler);
  }

  @Test public void localServiceName() {
    tracer = Tracer.newBuilder().localServiceName("my-foo").build();

    assertThat(tracer.localEndpoint.serviceName)
        .isEqualTo("my-foo");
  }

  @Test public void localServiceName_defaultIsUnknown() {
    assertThat(tracer.localEndpoint.serviceName)
        .isEqualTo("unknown");
  }

  @Test public void localServiceName_ignoredWhenGivenLocalEndpoint() {
    Endpoint localEndpoint = Endpoint.create("my-bar", 127 << 24 | 1);
    tracer = Tracer.newBuilder().localServiceName("my-foo")
        .localEndpoint(localEndpoint).build();

    assertThat(tracer.localEndpoint)
        .isSameAs(localEndpoint);
  }

  @Test public void clock() {
    Clock clock = () -> 0L;
    tracer = Tracer.newBuilder().clock(clock).build();

    assertThat(tracer.clock())
        .isSameAs(clock);
  }

  @Test public void newTrace_isRootSpan() {
    assertThat(tracer.newTrace())
        .satisfies(s -> assertThat(s.context().parentId()).isNull())
        .isInstanceOf(RealSpan.class);
  }

  @Test public void newTrace_traceId128Bit() {
    tracer = Tracer.newBuilder().traceId128Bit(true).build();

    assertThat(tracer.newTrace().context().traceIdHigh())
        .isNotZero();
  }

  @Test public void newTrace_unsampled_tracer() {
    tracer = Tracer.newBuilder().sampler(Sampler.NEVER_SAMPLE).build();

    assertThat(tracer.newTrace())
        .isInstanceOf(NoopSpan.class);
  }

  @Test public void newTrace_sampled_flag() {
    assertThat(tracer.newTrace(SamplingFlags.SAMPLED))
        .isInstanceOf(RealSpan.class);
  }

  @Test public void newTrace_debug_flag() {
    List<zipkin.Span> spans = new ArrayList<>();
    tracer = Tracer.newBuilder().reporter(spans::add).build();

    Span root = tracer.newTrace(SamplingFlags.DEBUG).start();
    root.finish();

    assertThat(spans).extracting(s -> s.debug)
        .containsExactly(true);
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

  @Test public void newChild_unsampledIsNoop() {
    TraceContext unsampled =
        tracer.newTrace().context().toBuilder().sampled(false).build();

    assertThat(tracer.newChild(unsampled))
        .isInstanceOf(NoopSpan.class);
  }
}

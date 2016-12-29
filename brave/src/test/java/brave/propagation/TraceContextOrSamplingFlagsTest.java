package brave.propagation;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TraceContextOrSamplingFlagsTest {

  @Test public void contextWhenIdsAreSet() {
    TraceContext.Builder builder = TraceContext.newBuilder().traceId(333L).spanId(1L);
    TraceContextOrSamplingFlags contextOrFlags = TraceContextOrSamplingFlags.create(builder);

    assertThat(contextOrFlags.context())
        .isEqualTo(builder.build());
    assertThat(contextOrFlags.samplingFlags())
        .isNull();
  }

  @Test public void contextWhenIdsAndSamplingAreSet() {
    TraceContext.Builder builder = TraceContext.newBuilder().traceId(333L).spanId(1L).sampled(true);
    TraceContextOrSamplingFlags contextOrFlags = TraceContextOrSamplingFlags.create(builder);

    assertThat(contextOrFlags.context())
        .isEqualTo(builder.build());
    assertThat(contextOrFlags.samplingFlags())
        .isNull();
  }

  @Test public void flagsWhenMissingTraceId() {
    TraceContext.Builder builder = TraceContext.newBuilder().spanId(1L);
    TraceContextOrSamplingFlags contextOrFlags = TraceContextOrSamplingFlags.create(builder);

    assertThat(contextOrFlags.context())
        .isNull();
    assertThat(contextOrFlags.samplingFlags())
        .isSameAs(SamplingFlags.EMPTY);
  }

  @Test public void flagsWhenMissingSpanId() {
    TraceContext.Builder builder = TraceContext.newBuilder().traceId(333L).sampled(true);
    TraceContextOrSamplingFlags contextOrFlags = TraceContextOrSamplingFlags.create(builder);

    assertThat(contextOrFlags.context())
        .isNull();
    assertThat(contextOrFlags.samplingFlags())
        .isSameAs(SamplingFlags.SAMPLED);
  }

  @Test public void flags() {
    TraceContext.Builder builder = TraceContext.newBuilder().sampled(true);
    TraceContextOrSamplingFlags contextOrFlags = TraceContextOrSamplingFlags.create(builder);

    assertThat(contextOrFlags.context())
        .isNull();
    assertThat(contextOrFlags.samplingFlags())
        .isSameAs(SamplingFlags.SAMPLED);
  }
}

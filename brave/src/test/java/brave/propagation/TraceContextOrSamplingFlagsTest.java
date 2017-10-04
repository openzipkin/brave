package brave.propagation;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TraceContextOrSamplingFlagsTest {

  @Test public void contextWhenIdsAreSet() {
    TraceContext.Builder builder = TraceContext.newBuilder().traceId(333L).spanId(1L);
    TraceContextOrSamplingFlags extracted = TraceContextOrSamplingFlags.create(builder.build());

    assertThat(extracted.context())
        .isEqualTo(builder.build());
    assertThat(extracted.traceIdContext())
        .isNull();
    assertThat(extracted.samplingFlags())
        .isNull();
  }

  @Test public void contextWhenIdsAndSamplingAreSet() {
    TraceContext.Builder builder = TraceContext.newBuilder().traceId(333L).spanId(1L).sampled(true);
    TraceContextOrSamplingFlags extracted = TraceContextOrSamplingFlags.create(builder.build());

    assertThat(extracted.context())
        .isEqualTo(builder.build());
    assertThat(extracted.traceIdContext())
        .isNull();
    assertThat(extracted.samplingFlags())
        .isNull();
  }

  @Test  @Deprecated public void contextWhenTraceIdAndSampledAreSet() {
    TraceIdContext.Builder builder = TraceIdContext.newBuilder().traceId(333L).sampled(true);
    TraceContextOrSamplingFlags extracted = TraceContextOrSamplingFlags.create(builder.build());

    assertThat(extracted.context())
        .isNull();
    assertThat(extracted.traceIdContext())
        .isEqualTo(builder.build());
    assertThat(extracted.samplingFlags())
        .isNull();
  }

  @Test @Deprecated public void deprecatedFlagsWhenMissingTraceId() {
    TraceContext.Builder builder = TraceContext.newBuilder().spanId(1L);
    TraceContextOrSamplingFlags extracted = TraceContextOrSamplingFlags.create(builder);

    assertThat(extracted.context())
        .isNull();
    assertThat(extracted.traceIdContext())
        .isNull();
    assertThat(extracted.samplingFlags())
        .isSameAs(SamplingFlags.EMPTY);
  }

  @Test  @Deprecated public void deprecatedFlagsWhenMissingSpanId() {
    TraceContext.Builder builder = TraceContext.newBuilder().traceId(333L).sampled(true);
    TraceContextOrSamplingFlags extracted = TraceContextOrSamplingFlags.create(builder);

    assertThat(extracted.context())
        .isNull();
    assertThat(extracted.traceIdContext())
        .isNull();
    assertThat(extracted.samplingFlags())
        .isSameAs(SamplingFlags.SAMPLED);
  }

  @Test public void flags() {
    TraceContextOrSamplingFlags extracted =
        TraceContextOrSamplingFlags.create(SamplingFlags.SAMPLED);

    assertThat(extracted.context())
        .isNull();
    assertThat(extracted.traceIdContext())
        .isNull();
    assertThat(extracted.samplingFlags())
        .isSameAs(SamplingFlags.SAMPLED);
  }
}

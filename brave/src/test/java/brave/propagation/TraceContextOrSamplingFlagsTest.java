package brave.propagation;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TraceContextOrSamplingFlagsTest {

  @Test public void create_sampledNullDebugFalse() {
    TraceContextOrSamplingFlags flags = TraceContextOrSamplingFlags.create(null, false);

    assertThat(flags).isSameAs(TraceContextOrSamplingFlags.EMPTY);
    assertThat(flags.sampled()).isNull();
    assertThat(flags.samplingFlags()).isSameAs(SamplingFlags.EMPTY);
  }

  @Test public void create_sampledNullDebugTrue() {
    TraceContextOrSamplingFlags flags = TraceContextOrSamplingFlags.create(null, true);

    assertThat(flags).isSameAs(TraceContextOrSamplingFlags.DEBUG);
    assertThat(flags.sampled()).isTrue();
    assertThat(flags.samplingFlags()).isSameAs(SamplingFlags.DEBUG);
  }

  @Test public void create_sampledTrueDebugFalse() {
    TraceContextOrSamplingFlags flags = TraceContextOrSamplingFlags.create(true, false);

    assertThat(flags).isSameAs(TraceContextOrSamplingFlags.SAMPLED);
    assertThat(flags.sampled()).isTrue();
    assertThat(flags.samplingFlags()).isSameAs(SamplingFlags.SAMPLED);
  }

  @Test public void create_sampledFalseDebugFalse() {
    TraceContextOrSamplingFlags flags = TraceContextOrSamplingFlags.create(false, false);

    assertThat(flags).isSameAs(TraceContextOrSamplingFlags.NOT_SAMPLED);
    assertThat(flags.sampled()).isFalse();
    assertThat(flags.samplingFlags()).isSameAs(SamplingFlags.NOT_SAMPLED);
  }

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

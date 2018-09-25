package brave.propagation;

import java.util.Collections;
import org.junit.Test;

import static brave.internal.InternalPropagation.FLAG_SAMPLED;
import static brave.internal.InternalPropagation.FLAG_SAMPLED_SET;
import static org.assertj.core.api.Assertions.assertThat;

public class TraceContextOrSamplingFlagsTest {
  TraceContext base = TraceContext.newBuilder().traceId(333L).spanId(1L).build();

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
    TraceContextOrSamplingFlags toTest = TraceContextOrSamplingFlags.create(base);

    assertThat(toTest.context())
        .isEqualTo(base);
    assertThat(toTest.traceIdContext())
        .isNull();
    assertThat(toTest.samplingFlags())
        .isNull();
  }

  @Test public void contextWhenIdsAndSamplingAreSet() {
    TraceContext.Builder builder = base.toBuilder().sampled(true);
    TraceContextOrSamplingFlags toTest = TraceContextOrSamplingFlags.create(builder.build());

    assertThat(toTest.context())
        .isEqualTo(builder.build());
    assertThat(toTest.traceIdContext())
        .isNull();
    assertThat(toTest.samplingFlags())
        .isNull();
  }

  @Test public void build_extra() {
    TraceContextOrSamplingFlags toTest = TraceContextOrSamplingFlags.newBuilder().context(base)
        .addExtra(1L).build();
    assertThat(toTest.extra()).isEmpty();
    assertThat(toTest.context().extra()).containsExactly(1L);

    TraceIdContext idContext = TraceIdContext.newBuilder().traceId(333L).build();
    toTest = TraceContextOrSamplingFlags.newBuilder().traceIdContext(idContext)
        .addExtra(1L).build();
    assertThat(toTest.extra()).containsExactly(1L);

    toTest = TraceContextOrSamplingFlags.newBuilder()
        .samplingFlags(SamplingFlags.SAMPLED).addExtra(1L).build();
    assertThat(toTest.extra()).containsExactly(1L);
  }

  @Test public void build_threeExtra() {
    TraceContext context = base.toBuilder()
        .extra(Collections.singletonList(1L)).build();
    TraceContextOrSamplingFlags toTest = TraceContextOrSamplingFlags.newBuilder().context(context)
        .addExtra(2L).addExtra(3L).build();
    assertThat(toTest.extra()).isEmpty();
    assertThat(toTest.context().extra())
        .containsExactly(1L, 2L, 3L);

    TraceIdContext idContext = TraceIdContext.newBuilder().traceId(333L).build();
    toTest = TraceContextOrSamplingFlags.newBuilder().traceIdContext(idContext)
        .addExtra(1L).addExtra(2L).addExtra(3L).build();
    assertThat(toTest.extra())
        .containsExactly(1L, 2L, 3L);

    toTest = TraceContextOrSamplingFlags.newBuilder().samplingFlags(SamplingFlags.SAMPLED)
        .addExtra(1L).addExtra(2L).addExtra(3L).build();
    assertThat(toTest.extra())
        .containsExactly(1L, 2L, 3L);
  }

  @Test public void flags() {
    TraceContextOrSamplingFlags toTest = TraceContextOrSamplingFlags.create(SamplingFlags.SAMPLED);

    assertThat(toTest.context())
        .isNull();
    assertThat(toTest.traceIdContext())
        .isNull();
    assertThat(toTest.samplingFlags())
        .isSameAs(SamplingFlags.SAMPLED);
  }

  @Test public void sampled_true() {
    assertThat(TraceContextOrSamplingFlags.create(base).sampled(true).value.flags)
        .isEqualTo(FLAG_SAMPLED_SET | FLAG_SAMPLED);

    TraceIdContext idContext = TraceIdContext.newBuilder().traceId(333L).build();
    assertThat(TraceContextOrSamplingFlags.create(idContext).sampled(true).value.flags)
        .isEqualTo(FLAG_SAMPLED_SET | FLAG_SAMPLED);

    assertThat(TraceContextOrSamplingFlags.EMPTY.sampled(true).value.flags)
        .isEqualTo(FLAG_SAMPLED_SET | FLAG_SAMPLED);
  }

  @Test public void sampled_false() {
    assertThat(TraceContextOrSamplingFlags.create(base).sampled(false).value.flags)
        .isEqualTo(FLAG_SAMPLED_SET);

    TraceIdContext idContext = TraceIdContext.newBuilder().traceId(333L).build();
    assertThat(TraceContextOrSamplingFlags.create(idContext).sampled(false).value.flags)
        .isEqualTo(FLAG_SAMPLED_SET);

    assertThat(TraceContextOrSamplingFlags.EMPTY.sampled(false).value.flags)
        .isEqualTo(FLAG_SAMPLED_SET);
  }

  @Test public void sampledLocal() {
    assertThat(TraceContextOrSamplingFlags.create(base).sampledLocal())
        .isFalse();
    assertThat(new TraceContextOrSamplingFlags.Builder().context(base).sampledLocal().build().sampledLocal())
        .isTrue();

    TraceIdContext idContext = TraceIdContext.newBuilder().traceId(333L).build();
    assertThat(TraceContextOrSamplingFlags.create(idContext).sampledLocal())
        .isFalse();
    assertThat(new TraceContextOrSamplingFlags.Builder().traceIdContext(idContext).sampledLocal().build().sampledLocal())
        .isTrue();

    assertThat(new TraceContextOrSamplingFlags.Builder().samplingFlags(SamplingFlags.SAMPLED).sampledLocal().build().sampledLocal())
        .isTrue();

    assertThat(TraceContextOrSamplingFlags.EMPTY.sampledLocal())
        .isFalse();
    assertThat(TraceContextOrSamplingFlags.SAMPLED.sampledLocal())
        .isFalse();
    assertThat(TraceContextOrSamplingFlags.NOT_SAMPLED.sampledLocal())
        .isFalse();
    assertThat(TraceContextOrSamplingFlags.DEBUG.sampledLocal())
        .isFalse();
  }

  @Test public void sampled_true_noop() {
    TraceContext context = base.toBuilder().sampled(true).build();
    TraceContextOrSamplingFlags toTest = TraceContextOrSamplingFlags.newBuilder().context(context)
        .addExtra(1L).build();
    assertThat(toTest.sampled(true)).isSameAs(toTest);

    TraceIdContext idContext = TraceIdContext.newBuilder().traceId(333L).sampled(true).build();
    toTest = TraceContextOrSamplingFlags.newBuilder().traceIdContext(idContext)
        .addExtra(1L).build();
    assertThat(toTest.sampled(true)).isSameAs(toTest);

    toTest = TraceContextOrSamplingFlags.newBuilder().samplingFlags(SamplingFlags.SAMPLED)
        .addExtra(1L).build();
    assertThat(toTest.sampled(true)).isSameAs(toTest);
  }

  @Test public void sampled_false_noop() {
    TraceContext context = base.toBuilder().sampled(false).build();
    TraceContextOrSamplingFlags toTest = TraceContextOrSamplingFlags.newBuilder().context(context)
        .addExtra(1L).build();
    assertThat(toTest.sampled(false)).isSameAs(toTest);

    TraceIdContext idContext = TraceIdContext.newBuilder().traceId(333L).sampled(false).build();
    toTest = TraceContextOrSamplingFlags.newBuilder().traceIdContext(idContext)
        .addExtra(1L).build();
    assertThat(toTest.sampled(false)).isSameAs(toTest);

    toTest = TraceContextOrSamplingFlags.newBuilder().samplingFlags(SamplingFlags.NOT_SAMPLED)
        .addExtra(1L).build();
    assertThat(toTest.sampled(false)).isSameAs(toTest);
  }
}

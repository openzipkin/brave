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
package brave.propagation;

import java.util.function.Supplier;
import org.junit.Test;

import static brave.propagation.SamplingFlags.EMPTY;
import static brave.propagation.SamplingFlags.NOT_SAMPLED;
import static brave.propagation.SamplingFlags.SAMPLED;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TraceContextOrSamplingFlagsTest {
  TraceContext context = TraceContext.newBuilder().traceId(333L).spanId(1L).sampled(true).build();
  TraceIdContext idContext = TraceIdContext.newBuilder().traceId(333L).sampled(true).build();

  TraceContextOrSamplingFlags extracted;

  @Test public void create_context() {
    extracted = TraceContextOrSamplingFlags.create(context);
    assertThat(extracted.context()).isSameAs(context);
    assertThat(extracted.traceIdContext()).isNull();
    assertThat(extracted.samplingFlags()).isNull();
  }

  @Test public void create_samplingFlags() {
    extracted = TraceContextOrSamplingFlags.create(SAMPLED);
    assertThat(extracted.context()).isNull();
    assertThat(extracted.traceIdContext()).isNull();
    assertThat(extracted.samplingFlags()).isSameAs(SAMPLED);
  }

  @Test public void create_traceIdContext() {
    extracted = TraceContextOrSamplingFlags.create(idContext);
    assertThat(extracted.context()).isNull();
    assertThat(extracted.traceIdContext()).isSameAs(idContext);
    assertThat(extracted.samplingFlags()).isNull();
  }

  @Test public void sampled_get_context() {
    assertThat(TraceContextOrSamplingFlags.create(context).sampled()).isTrue();

    extracted = TraceContextOrSamplingFlags.create(context.toBuilder().sampled(null).build());
    assertThat(extracted.sampled()).isNull();
  }

  @Test public void sampled_get_samplingFlags() {
    assertThat(TraceContextOrSamplingFlags.create(SAMPLED).sampled()).isTrue();

    extracted = TraceContextOrSamplingFlags.create(EMPTY);
    assertThat(extracted.sampled()).isNull();
  }

  @Test public void sampled_get_traceIdContext() {
    assertThat(TraceContextOrSamplingFlags.create(idContext).sampled()).isTrue();

    extracted = TraceContextOrSamplingFlags.create(idContext.toBuilder().sampled(null).build());
    assertThat(extracted.sampled()).isNull();
  }

  @Test public void sampled_set_context() {
    extracted = TraceContextOrSamplingFlags.create(context);
    assertThat(extracted.sampled(true)).isSameAs(extracted);
    assertThat(extracted.sampled(false).sampled()).isFalse();
    assertThat(extracted.sampled(false).context().sampled()).isFalse();

    extracted = TraceContextOrSamplingFlags.create(context.toBuilder().sampled(null).build());
    assertThat(extracted.sampled(true).sampled()).isTrue();
    assertThat(extracted.sampled(false).sampled()).isFalse();
    assertThat(extracted.sampled(true).context().sampled()).isTrue();
    assertThat(extracted.sampled(false).context().sampled()).isFalse();
  }

  @Test public void sampled_set_samplingFlags() {
    extracted = TraceContextOrSamplingFlags.create(SAMPLED);
    assertThat(extracted.sampled(true)).isSameAs(extracted);
    assertThat(extracted.sampled(false).sampled()).isFalse();

    extracted = TraceContextOrSamplingFlags.create(EMPTY);
    assertThat(extracted.sampled(true).sampled()).isTrue();
    assertThat(extracted.sampled(false).sampled()).isFalse();
  }

  @Test public void sampled_set_traceIdContext() {
    extracted = TraceContextOrSamplingFlags.create(idContext);
    assertThat(extracted.sampled(true)).isSameAs(extracted);
    assertThat(extracted.sampled(false).sampled()).isFalse();
    assertThat(extracted.sampled(false).traceIdContext().sampled()).isFalse();

    extracted = TraceContextOrSamplingFlags.create(idContext.toBuilder().sampled(null).build());
    assertThat(extracted.sampled(true).sampled()).isTrue();
    assertThat(extracted.sampled(false).sampled()).isFalse();
    assertThat(extracted.sampled(true).traceIdContext().sampled()).isTrue();
    assertThat(extracted.sampled(false).traceIdContext().sampled()).isFalse();
  }

  @Test public void sampled_set_keepsExtra_context() {
    extracted = TraceContextOrSamplingFlags.newBuilder(context).addExtra(1L).build();
    assertThat(extracted.sampled(false).context().extra()).contains(1L);
  }

  @Test public void sampled_set_keepsExtra_samplingFlags() {
    extracted = TraceContextOrSamplingFlags.newBuilder(SAMPLED).addExtra(1L).build();
    assertThat(extracted.sampled(false).extra()).contains(1L);
  }

  @Test public void sampled_set_keepsExtra_traceIdContext() {
    extracted = TraceContextOrSamplingFlags.newBuilder(idContext).addExtra(1L).build();
    assertThat(extracted.sampled(false).extra()).contains(1L);
  }

  @Test public void sampled_set_keepsSampledLocal_context() {
    extracted = TraceContextOrSamplingFlags.newBuilder(context).sampledLocal().build();

    extracted = extracted.sampled(false);
    assertThat(extracted.sampled()).isFalse();
    assertThat(extracted.sampledLocal()).isTrue();
  }

  @Test public void sampled_set_keepsSampledLocal_samplingFlags() {
    extracted = TraceContextOrSamplingFlags.newBuilder(SAMPLED).sampledLocal().build();

    extracted = extracted.sampled(false);
    assertThat(extracted.sampled()).isFalse();
    assertThat(extracted.sampledLocal()).isTrue();
  }

  @Test public void sampled_set_keepsSampledLocal_traceIdContext() {
    extracted = TraceContextOrSamplingFlags.newBuilder(idContext).sampledLocal().build();

    extracted = extracted.sampled(false);
    assertThat(extracted.sampled()).isFalse();
    assertThat(extracted.sampledLocal()).isTrue();
  }

  @Test public void newBuilder_context() {
    extracted = TraceContextOrSamplingFlags.newBuilder(context).build();
    assertThat(extracted.context()).isSameAs(context);
    assertThat(extracted.traceIdContext()).isNull();
    assertThat(extracted.samplingFlags()).isNull();
  }

  @Test public void newBuilder_samplingFlags() {
    extracted = TraceContextOrSamplingFlags.newBuilder(SAMPLED).build();
    assertThat(extracted.context()).isNull();
    assertThat(extracted.traceIdContext()).isNull();
    assertThat(extracted.samplingFlags()).isSameAs(SAMPLED);
  }

  @Test public void newBuilder_traceIdContext() {
    extracted = TraceContextOrSamplingFlags.newBuilder(idContext).build();
    assertThat(extracted.context()).isNull();
    assertThat(extracted.traceIdContext()).isSameAs(idContext);
    assertThat(extracted.samplingFlags()).isNull();
  }

  @Test public void builder_addExtra_context() {
    extracted = TraceContextOrSamplingFlags.newBuilder(context).addExtra(1L).build();
    assertThat(extracted.context().extra()).containsExactly(1L);
    assertThat(extracted.extra()).isEmpty();
  }

  @Test public void builder_addExtra_samplingFlags() {
    extracted = TraceContextOrSamplingFlags.newBuilder(SAMPLED).addExtra(1L).build();
    assertThat(extracted.extra()).containsExactly(1L);
  }

  @Test public void builder_addExtra_traceIdContext() {
    extracted = TraceContextOrSamplingFlags.newBuilder(idContext).addExtra(1L).build();
    assertThat(extracted.extra()).containsExactly(1L);
  }

  @Test public void builder_addExtra_toExisting_context() {
    extracted = TraceContextOrSamplingFlags.newBuilder(context).addExtra(1L).build();

    extracted = extracted.toBuilder().addExtra(2L).build();
    assertThat(extracted.context().extra()).containsExactly(1L, 2L);
    assertThat(extracted.extra()).isEmpty();


    assertThatThrownBy(() -> extracted.context().extra().add(3L))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> extracted.extra().add(3L))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test public void builder_addExtra_toExisting_samplingFlags() {
    extracted = TraceContextOrSamplingFlags.newBuilder(SAMPLED).addExtra(1L).build();

    extracted = extracted.toBuilder().addExtra(2L).build();
    assertThat(extracted.extra()).containsExactly(1L, 2L);

    assertThatThrownBy(() -> extracted.extra().add(3L))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test public void builder_addExtra_toExisting_traceIdContext() {
    extracted = TraceContextOrSamplingFlags.newBuilder(idContext).addExtra(1L).build();

    extracted = extracted.toBuilder().addExtra(2L).build();
    assertThat(extracted.extra()).containsExactly(1L, 2L);

    assertThatThrownBy(() -> extracted.extra().add(3L))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test public void builder_addExtra_redundantIgnored_context() {
    extracted = TraceContextOrSamplingFlags.newBuilder(context).addExtra(1L).build();

    extracted = extracted.toBuilder().addExtra(1L).build();
    assertThat(extracted.context().extra()).containsExactly(1L);
    assertThat(extracted.extra()).isEmpty();
  }

  @Test public void builder_addExtra_redundantIgnored_samplingFlags() {
    extracted = TraceContextOrSamplingFlags.newBuilder(SAMPLED).addExtra(1L).build();

    extracted = extracted.toBuilder().addExtra(1L).build();
    assertThat(extracted.extra()).containsExactly(1L);
  }

  @Test public void builder_addExtra_redundantIgnored_traceIdContext() {
    extracted = TraceContextOrSamplingFlags.newBuilder(idContext).addExtra(1L).build();
    extracted = extracted.toBuilder().addExtra(1L).build();

    assertThat(extracted.extra()).containsExactly(1L);
  }

  @Test public void builder_sampledLocal_context() {
    assertThat(TraceContextOrSamplingFlags.create(context).sampledLocal())
        .isFalse();
    assertThat(TraceContextOrSamplingFlags.newBuilder(context)
        .sampledLocal().build().sampledLocal())
        .isTrue();
  }

  @Test public void builder_sampledLocal_samplingFlags() {
    assertThat(TraceContextOrSamplingFlags.newBuilder(SAMPLED)
        .sampledLocal().build().sampledLocal())
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

  @Test public void builder_sampledLocal_traceIdContext() {
    assertThat(TraceContextOrSamplingFlags.create(idContext).sampledLocal())
        .isFalse();
    assertThat(TraceContextOrSamplingFlags.newBuilder(idContext)
        .sampledLocal().build().sampledLocal())
        .isTrue();
  }

  @Test public void equalsAndHashCode_context() {
    equalsAndHashCode(
        () -> TraceContextOrSamplingFlags.create(context),
        () -> TraceContextOrSamplingFlags.create(context.toBuilder().traceId(111L).build()),
        () -> TraceContextOrSamplingFlags.create(SAMPLED)
    );
  }

  @Test public void equalsAndHashCode_samplingFlags() {
    equalsAndHashCode(
        () -> TraceContextOrSamplingFlags.create(SAMPLED),
        () -> TraceContextOrSamplingFlags.create(NOT_SAMPLED),
        () -> TraceContextOrSamplingFlags.create(context)
    );
  }

  @Test public void equalsAndHashCode_traceIdContext() {
    equalsAndHashCode(
        () -> TraceContextOrSamplingFlags.create(idContext),
        () -> TraceContextOrSamplingFlags.create(idContext.toBuilder().traceId(111L).build()),
        () -> TraceContextOrSamplingFlags.create(SAMPLED)
    );
  }

  void equalsAndHashCode(
      Supplier<TraceContextOrSamplingFlags> factory,
      Supplier<TraceContextOrSamplingFlags> differentValueFactory,
      Supplier<TraceContextOrSamplingFlags> differentTypeFactory
  ) {
    // same extracted are equivalent
    extracted = factory.get();
    assertThat(extracted).isEqualTo(extracted);
    assertThat(extracted).hasSameHashCodeAs(extracted);

    // different extracted is equivalent
    TraceContextOrSamplingFlags sameState = factory.get();
    assertThat(extracted).isEqualTo(sameState);
    assertThat(extracted).hasSameHashCodeAs(sameState);

    // different values are not equivalent
    TraceContextOrSamplingFlags differentValue = differentValueFactory.get();
    assertThat(extracted).isNotEqualTo(differentValue);
    assertThat(differentValue).isNotEqualTo(extracted);
    assertThat(extracted.hashCode()).isNotEqualTo(differentValue);

    // different extra are not equivalent
    TraceContextOrSamplingFlags withExtra = extracted.toBuilder().addExtra(1L).build();
    assertThat(extracted).isNotEqualTo(withExtra);
    assertThat(withExtra).isNotEqualTo(extracted);
    assertThat(extracted.hashCode()).isNotEqualTo(withExtra);

    // different type are not equivalent
    TraceContextOrSamplingFlags differentType = differentTypeFactory.get();
    assertThat(extracted).isNotEqualTo(differentType);
    assertThat(differentType).isNotEqualTo(extracted);
    assertThat(extracted.hashCode()).isNotEqualTo(differentType);
  }

  @Test public void toString_context() {
    toString(
        () -> TraceContextOrSamplingFlags.create(context),
        "Extracted{traceContext=000000000000014d/0000000000000001, samplingFlags=SAMPLED_REMOTE}",
        "Extracted{traceContext=000000000000014d/0000000000000001, samplingFlags=SAMPLED_REMOTE, extra=[1, 2]}"
    );
  }

  @Test public void toString_samplingFlags() {
    toString(
        () -> TraceContextOrSamplingFlags.create(SAMPLED),
        "Extracted{samplingFlags=SAMPLED_REMOTE}",
        "Extracted{samplingFlags=SAMPLED_REMOTE, extra=[1, 2]}"
    );
  }

  @Test public void toString_traceIdContext() {
    toString(
        () -> TraceContextOrSamplingFlags.create(idContext),
        "Extracted{traceIdContext=000000000000014d, samplingFlags=SAMPLED_REMOTE}",
        "Extracted{traceIdContext=000000000000014d, samplingFlags=SAMPLED_REMOTE, extra=[1, 2]}"
    );
  }

  void toString(
      Supplier<TraceContextOrSamplingFlags> factory, String toString, String toStringWithExtra) {
    extracted = factory.get();
    assertThat(extracted).hasToString(toString);

    extracted = factory.get().toBuilder().addExtra(1L).addExtra(2L).build();
    assertThat(extracted).hasToString(toStringWithExtra);
  }

  @Deprecated @Test public void deprecated_create_sampledNullDebugFalse() {
    extracted = TraceContextOrSamplingFlags.create(null, false);
    assertThat(extracted).isSameAs(TraceContextOrSamplingFlags.EMPTY);
    assertThat(extracted.sampled()).isNull();
    assertThat(extracted.samplingFlags()).isSameAs(SamplingFlags.EMPTY);
  }

  @Deprecated @Test public void deprecated_create_sampledNullDebugTrue() {
    extracted = TraceContextOrSamplingFlags.create(null, true);
    assertThat(extracted).isSameAs(TraceContextOrSamplingFlags.DEBUG);
    assertThat(extracted.sampled()).isTrue();
    assertThat(extracted.samplingFlags()).isSameAs(SamplingFlags.DEBUG);
  }

  @Deprecated @Test public void deprecated_create_sampledTrueDebugFalse() {
    extracted = TraceContextOrSamplingFlags.create(true, false);
    assertThat(extracted).isSameAs(TraceContextOrSamplingFlags.SAMPLED);
    assertThat(extracted.sampled()).isTrue();
    assertThat(extracted.samplingFlags()).isSameAs(SAMPLED);
  }

  @Deprecated @Test public void deprecated_create_sampledFalseDebugFalse() {
    extracted = TraceContextOrSamplingFlags.create(false, false);
    assertThat(extracted).isSameAs(TraceContextOrSamplingFlags.NOT_SAMPLED);
    assertThat(extracted.sampled()).isFalse();
    assertThat(extracted.samplingFlags()).isSameAs(SamplingFlags.NOT_SAMPLED);
  }

  @Deprecated @Test public void deprecated_sampled_set_null_context() {
    extracted = TraceContextOrSamplingFlags.create(context.toBuilder().sampled(null).build());
    assertThat(extracted.sampled(null)).isSameAs(extracted);

    extracted = TraceContextOrSamplingFlags.create(context);
    assertThat(extracted.sampled(null).sampled()).isNull();
    assertThat(extracted.sampled(null).context().sampled()).isNull();
  }

  @Deprecated @Test public void deprecated_sampled_set_null_samplingFlags() {
    extracted = TraceContextOrSamplingFlags.create(EMPTY);
    assertThat(extracted.sampled(null)).isSameAs(extracted);

    extracted = TraceContextOrSamplingFlags.create(SAMPLED);
    assertThat(extracted.sampled(null).sampled()).isNull();
  }

  @Deprecated @Test public void deprecated_sampled_set_null_traceIdContext() {
    extracted = TraceContextOrSamplingFlags.create(idContext.toBuilder().sampled(null).build());
    assertThat(extracted.sampled(null)).isSameAs(extracted);

    extracted = TraceContextOrSamplingFlags.create(idContext);
    assertThat(extracted.sampled(null).sampled()).isNull();
    assertThat(extracted.sampled(null).traceIdContext().sampled()).isNull();
  }

  @Deprecated @Test public void deprecated_sampled_set_null_keepsExtra_context() {
    extracted = TraceContextOrSamplingFlags.newBuilder(context).addExtra(1L).build();
    assertThat(extracted.sampled(null).context().extra()).contains(1L);
  }

  @Deprecated @Test public void deprecated_sampled_set_null_keepsExtra_samplingFlags() {
    extracted = TraceContextOrSamplingFlags.newBuilder(SAMPLED).addExtra(1L).build();
    assertThat(extracted.sampled(null).extra()).contains(1L);
  }

  @Deprecated @Test public void deprecated_sampled_set_null_keepsExtra_traceIdContext() {
    extracted = TraceContextOrSamplingFlags.newBuilder(idContext).addExtra(1L).build();
    assertThat(extracted.sampled(null).extra()).contains(1L);
  }

  @Deprecated @Test public void deprecated_sampled_set_null_keepsSampledLocal_context() {
    extracted = TraceContextOrSamplingFlags.newBuilder(context).sampledLocal().build();

    extracted = extracted.sampled(null);
    assertThat(extracted.sampled()).isNull();
    assertThat(extracted.sampledLocal()).isTrue();
  }

  @Deprecated @Test public void deprecated_sampled_set_null_keepsSampledLocal_samplingFlags() {
    extracted = TraceContextOrSamplingFlags.newBuilder(SAMPLED).sampledLocal().build();

    extracted = extracted.sampled(null);
    assertThat(extracted.sampled()).isNull();
    assertThat(extracted.sampledLocal()).isTrue();
  }

  @Deprecated @Test public void deprecated_sampled_set_null_keepsSampledLocal_traceIdContext() {
    extracted = TraceContextOrSamplingFlags.newBuilder(idContext).sampledLocal().build();

    extracted = extracted.sampled(null);
    assertThat(extracted.sampled()).isNull();
    assertThat(extracted.sampledLocal()).isTrue();
  }

  @Deprecated @Test public void deprecated_builder_invalid() {
    TraceContextOrSamplingFlags.Builder builder = TraceContextOrSamplingFlags.newBuilder();
    assertThatThrownBy(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Value unset. Use a non-deprecated newBuilder method instead.");
  }

  @Deprecated @Test public void deprecated_builder_extraList_context() {
    extracted = TraceContextOrSamplingFlags.newBuilder(context).addExtra(3L).build();

    extracted = extracted.toBuilder().extra(asList(1L, 2L)).build();
    assertThat(extracted.context().extra()).containsExactly(1L, 2L);
    assertThat(extracted.extra()).isEmpty();

    assertThatThrownBy(() -> extracted.context().extra().add(3L))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> extracted.extra().add(3L))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Deprecated @Test public void deprecated_builder_extraList_samplingFlags() {
    extracted = TraceContextOrSamplingFlags.newBuilder(SAMPLED).addExtra(3L).build();

    extracted = extracted.toBuilder().extra(asList(1L, 2L)).build();
    assertThat(extracted.extra()).containsExactly(1L, 2L);

    assertThatThrownBy(() -> extracted.extra().add(3L))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Deprecated @Test public void deprecated_builder_extraList_traceIdContext() {
    extracted = TraceContextOrSamplingFlags.newBuilder(idContext).addExtra(3L).build();

    extracted = extracted.toBuilder().extra(asList(1L, 2L)).build();
    assertThat(extracted.extra()).containsExactly(1L, 2L);

    assertThatThrownBy(() -> extracted.extra().add(3L))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Deprecated @Test public void deprecated_builder_context() {
    extracted = TraceContextOrSamplingFlags.newBuilder().context(context).build();
    assertThat(extracted.context()).isSameAs(context);
    assertThat(extracted.traceIdContext()).isNull();
    assertThat(extracted.samplingFlags()).isNull();
  }

  @Deprecated @Test public void deprecated_builder_samplingFlags() {
    extracted = TraceContextOrSamplingFlags.newBuilder().samplingFlags(SAMPLED).build();
    assertThat(extracted.context()).isNull();
    assertThat(extracted.traceIdContext()).isNull();
    assertThat(extracted.samplingFlags()).isSameAs(SAMPLED);
  }

  @Deprecated @Test public void deprecated_builder_traceIdContext() {
    extracted = TraceContextOrSamplingFlags.newBuilder().traceIdContext(idContext).build();
    assertThat(extracted.context()).isNull();
    assertThat(extracted.traceIdContext()).isSameAs(idContext);
    assertThat(extracted.samplingFlags()).isNull();
  }

  @Deprecated @Test public void deprecated_builder_late_context() {
    extracted = TraceContextOrSamplingFlags.newBuilder()
        .sampledLocal()
        .addExtra(1L)
        .context(context).build();

    assertThat(extracted.sampledLocal()).isTrue();
    assertThat(extracted.context().extra()).containsExactly(1L);
    assertThat(extracted.extra()).isEmpty();
  }

  @Deprecated @Test public void deprecated_builder_late_samplingFlags() {
    extracted = TraceContextOrSamplingFlags.newBuilder()
        .sampledLocal()
        .addExtra(1L)
        .samplingFlags(EMPTY).build();

    assertThat(extracted.sampledLocal()).isTrue();
    assertThat(extracted.extra()).containsExactly(1L);
  }

  @Deprecated @Test public void deprecated_builder_late_traceIdContext() {
    extracted = TraceContextOrSamplingFlags.newBuilder()
        .sampledLocal()
        .addExtra(1L)
        .traceIdContext(idContext).build();

    assertThat(extracted.sampledLocal()).isTrue();
    assertThat(extracted.extra()).containsExactly(1L);
  }
}

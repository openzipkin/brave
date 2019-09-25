/*
 * Copyright 2013-2019 The OpenZipkin Authors
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
package brave.internal;

import brave.ScopedSpan;
import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public abstract class PropagationFieldsFactoryTest<K, V, P extends PropagationFields<K, V>>
  extends ExtraFactoryTest<P, PropagationFieldsFactory<K, V, P>> {
  protected final K keyOne, keyTwo;
  protected final V valueOne, valueTwo, valueThree;

  protected PropagationFieldsFactoryTest(K keyOne, K keyTwo, V valueOne, V valueTwo, V valueThree) {
    this.keyOne = keyOne;
    this.keyTwo = keyTwo;
    this.valueOne = valueOne;
    this.valueTwo = valueTwo;
    this.valueThree = valueThree;
  }

  @Test public void contextsAreIndependent() {
    try (Tracing tracing = Tracing.newBuilder().propagationFactory(propagationFactory).build()) {
      TraceContext context1 = tracing.tracer().nextSpan().context();
      PropagationFields.put(context1, keyOne, valueOne, factory.type());
      TraceContext context2 = tracing.tracer().newChild(context1).context();

      // Instances are not the same
      assertThat(context1.findExtra(factory.type()))
        .isNotSameAs(context2.findExtra(factory.type()));

      // But have the same values
      assertThat(context1.findExtra(factory.type()).toMap())
        .isEqualTo(context2.findExtra(factory.type()).toMap());
      assertThat(PropagationFields.get(context1, keyOne, factory.type()))
        .isEqualTo(PropagationFields.get(context2, keyOne, factory.type()))
        .isEqualTo(valueOne);

      PropagationFields.put(context1, keyOne, valueTwo, factory.type());
      PropagationFields.put(context2, keyOne, valueThree, factory.type());

      // Yet downstream changes don't affect eachother
      assertThat(PropagationFields.get(context1, keyOne, factory.type()))
        .isEqualTo(valueTwo);
      assertThat(PropagationFields.get(context2, keyOne, factory.type()))
        .isEqualTo(valueThree);
    }
  }

  @Test public void contextIsntBrokenWithSmallChanges() {
    try (Tracing tracing = Tracing.newBuilder().propagationFactory(propagationFactory).build()) {

      TraceContext context1 = tracing.tracer().nextSpan().context();
      PropagationFields.put(context1, keyOne, valueOne, factory.type());

      TraceContext context2 =
        tracing.tracer().toSpan(context1.toBuilder().sampled(false).build()).context();
      PropagationFields<K, V> fields1 = (PropagationFields<K, V>) context1.extra().get(0);
      PropagationFields<K, V> fields2 = (PropagationFields<K, V>) context2.extra().get(0);

      // we have the same span ID, so we should couple our extra fields
      assertThat(fields1).isSameAs(fields2);

      // we no longer have the same span ID, so we should decouple our extra fields
      TraceContext context3 = tracing.tracer().newChild(context1).context();
      PropagationFields fields3 = (PropagationFields) context3.extra().get(0);

      // we have different instances of extra
      assertThat(fields1).isNotSameAs(fields3);

      // however, the values inside are the same until a write occurs
      assertThat(fields1.toMap()).isEqualTo(fields3.toMap());

      // inside the span, the same change is present, but the other span has the old values
      PropagationFields.put(context1, keyOne, valueTwo, factory.type());
      assertThat(fields1).isEqualToComparingFieldByField(fields2);
      assertThat(fields3.get(keyOne)).isEqualTo(valueOne);
    }
  }

  /**
   * This scenario is possible, albeit rare. {@code tracer.nextSpan(extracted} } is called when
   * there is an implicit parent. For example, you have a trace in progress when extracting trace
   * state from an incoming message. Another example is where there is a span in scope due to a leak
   * such as from using {@link CurrentTraceContext.Default#inheritable()}.
   *
   * <p>When we are only extracting extra fields, the state should merge as opposed to creating
   * duplicate copies of {@link PropagationFields}.
   */
  @Test public void nextSpanMergesExtraWithImplicitParent_hasFields() {
    try (Tracing tracing = Tracing.newBuilder().propagationFactory(propagationFactory).build()) {
      ScopedSpan parent = tracing.tracer().startScopedSpan("parent");
      try {
        PropagationFields.put(parent.context(), keyOne, valueOne, factory.type());

        PropagationFields<K, V> extractedPropagationFields = factory.create();
        extractedPropagationFields.put(keyOne, valueTwo); // extracted should win!
        extractedPropagationFields.put(keyTwo, valueThree);

        TraceContextOrSamplingFlags extracted = TraceContextOrSamplingFlags.newBuilder()
          .samplingFlags(SamplingFlags.EMPTY)
          .addExtra(extractedPropagationFields)
          .build();

        TraceContext context1 = tracing.tracer().nextSpan(extracted).context();

        assertThat(context1.extra()) // merged
          .hasSize(1);
        PropagationFields<K, V> fields = ((PropagationFields<K, V>) context1.extra().get(0));
        assertThat(fields.toMap()).containsExactly(
          entry(keyOne, valueTwo),
          entry(keyTwo, valueThree)
        );
        assertThat(fields).extracting("traceId", "spanId")
          .containsExactly(context1.traceId(), context1.spanId());
      } finally {
        parent.finish();
      }
    }
  }

  @Test public void nextSpanExtraWithImplicitParent_butNoImplicitExtraFields() {
    try (Tracing tracing = Tracing.newBuilder().propagationFactory(propagationFactory).build()) {

      ScopedSpan parent = tracing.tracer().startScopedSpan("parent");
      try {
        PropagationFields<K, V> extractedPropagationFields = factory.create();
        extractedPropagationFields.put(keyTwo, valueThree);

        TraceContextOrSamplingFlags extracted = TraceContextOrSamplingFlags.newBuilder()
          .samplingFlags(SamplingFlags.EMPTY)
          .addExtra(extractedPropagationFields)
          .build();

        TraceContext context1 = tracing.tracer().nextSpan(extracted).context();

        assertThat(context1.extra()).hasSize(1); // merged

        PropagationFields<K, V> fields = ((PropagationFields<K, V>) context1.extra().get(0));
        assertThat(fields.toMap())
          .containsEntry(keyTwo, valueThree);
        assertThat(fields).extracting("traceId", "spanId")
          .containsExactly(context1.traceId(), context1.spanId());
      } finally {
        parent.finish();
      }
    }
  }

  @Test public void nextSpanExtraWithImplicitParent_butNoExtractedExtraFields() {
    try (Tracing tracing = Tracing.newBuilder().propagationFactory(propagationFactory).build()) {

      ScopedSpan parent = tracing.tracer().startScopedSpan("parent");
      try {
        PropagationFields.put(parent.context(), keyOne, valueOne, factory.type());

        TraceContextOrSamplingFlags extracted = TraceContextOrSamplingFlags.newBuilder()
          .samplingFlags(SamplingFlags.EMPTY)
          .build();

        // TODO didn't pass the reference from parent
        TraceContext context1 = tracing.tracer().nextSpan(extracted).context();

        assertThat(context1.extra()).hasSize(1); // didn't duplicate

        PropagationFields<K, V> fields = ((PropagationFields<K, V>) context1.extra().get(0));
        assertThat(fields.toMap())
          .containsEntry(keyOne, valueOne);
        assertThat(fields).extracting("traceId", "spanId")
          .containsExactly(context1.traceId(), context1.spanId());
      } finally {
        parent.finish();
      }
    }
  }

  @Test public void get() {
    TraceContext context =
      propagationFactory.decorate(TraceContext.newBuilder().traceId(1).spanId(2).build());
    PropagationFields.put(context, keyTwo, valueThree, factory.type());

    assertThat(PropagationFields.get(context, keyTwo, factory.type()))
      .isEqualTo(valueThree);
  }

  @Test public void get_null_if_not_set() {
    assertThat(PropagationFields.get(context, keyTwo, factory.type()))
      .isNull();
  }

  @Test public void get_ignore_if_not_defined() {
    assertThat(PropagationFields.get(context, keyOne, factory.type()))
      .isNull();
  }

  @Override
  protected void assertAssociatedWith(PropagationFields extra, long traceId, long spanId) {
    assertThat(extra.traceId).isEqualTo(traceId);
    assertThat(extra.spanId).isEqualTo(spanId);
  }

  @Test public void isEmpty() {
    PropagationFields<K, V> fields = factory.create();
    assertThat(fields.isEmpty()).isTrue();

    fields.put(keyOne, valueOne);
    assertThat(fields.isEmpty()).isFalse();
  }

  @Test public void toMap_one() {
    PropagationFields<K, V> fields = factory.create();
    fields.put(keyTwo, valueThree);

    assertThat(fields.toMap())
      .hasSize(1)
      .containsEntry(keyTwo, valueThree);
  }

  @Test public void toMap_two() {
    PropagationFields<K, V> fields = factory.create();
    fields.put(keyOne, valueOne);
    fields.put(keyTwo, valueThree);

    assertThat(fields.toMap())
      .hasSize(2)
      .containsEntry(keyOne, valueOne)
      .containsEntry(keyTwo, valueThree);
  }

  @Test public void toString_one() {
    PropagationFields<K, V> fields = factory.create();
    fields.put(keyTwo, valueThree);

    assertThat(fields.toString())
      .contains("{" + keyTwo + "=" + valueThree + "}");
  }

  @Test public void toString_two() {
    PropagationFields<K, V> fields = factory.create();
    fields.put(keyOne, valueOne);
    fields.put(keyTwo, valueThree);

    assertThat(fields.toString())
      .contains("{" + keyOne + "=" + valueOne + ", " + keyTwo + "=" + valueThree + "}");
  }
}

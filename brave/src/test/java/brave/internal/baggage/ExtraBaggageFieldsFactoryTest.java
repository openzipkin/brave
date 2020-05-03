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
package brave.internal.baggage;

import brave.ScopedSpan;
import brave.Tracing;
import brave.baggage.BaggageField;
import brave.internal.InternalPropagation;
import brave.propagation.B3Propagation;
import brave.propagation.CurrentTraceContext;
import brave.propagation.Propagation;
import brave.propagation.StrictCurrentTraceContext;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.junit.Test;
import zipkin2.reporter.Reporter;

import static brave.propagation.SamplingFlags.EMPTY;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class ExtraBaggageFieldsFactoryTest {
  BaggageField field1 = BaggageField.create("one");
  BaggageField field2 = BaggageField.create("two");
  String value1 = "1", value2 = "2", value3 = "3";

  ExtraBaggageFieldsFactory factory = FixedBaggageFieldsFactory.newFactory(asList(field1, field2));

  Propagation.Factory propagationFactory = new Propagation.Factory() {
    @Override public <K> Propagation<K> create(Propagation.KeyFactory<K> keyFactory) {
      return B3Propagation.FACTORY.create(keyFactory);
    }

    @Override public TraceContext decorate(TraceContext context) {
      return factory.decorate(context);
    }
  };
  TraceContext context = propagationFactory.decorate(TraceContext.newBuilder()
    .traceId(1L)
    .spanId(2L)
    .sampled(true)
    .build());

  @Test public void contextsAreIndependent() {
    try (Tracing tracing = withNoopSpanReporter()) {
      TraceContext context1 = tracing.tracer().nextSpan().context();
      field1.updateValue(context1, value1);

      TraceContext context2 = tracing.tracer().newChild(context1).context();

      // Instances are not the same
      assertThat(context1.findExtra(ExtraBaggageFields.class))
        .isNotSameAs(context2.findExtra(ExtraBaggageFields.class));

      // But have the same values
      assertThat(context1.findExtra(ExtraBaggageFields.class))
        .isEqualTo(context2.findExtra(ExtraBaggageFields.class));
      assertThat(field1.getValue(context1))
        .isEqualTo(field1.getValue(context2))
        .isEqualTo(value1);

      field1.updateValue(context1, value2);
      field1.updateValue(context2, value3);

      // Yet downstream changes don't affect eachother
      assertThat(field1.getValue(context1)).isEqualTo(value2);
      assertThat(field1.getValue(context2)).isEqualTo(value3);
    }
  }

  @Test public void contextsHaveIndependentValue() {
    try (Tracing tracing = withNoopSpanReporter()) {

      TraceContext context1 = tracing.tracer().nextSpan().context();
      field1.updateValue(context1, value1);

      TraceContext context2 =
        tracing.tracer().toSpan(context1.toBuilder().sampled(false).build()).context();
      ExtraBaggageFields extra1 = (ExtraBaggageFields) context1.extra().get(0);
      ExtraBaggageFields extra1_toSpan = (ExtraBaggageFields) context2.extra().get(0);

      // we have the same span ID, so we should couple our baggage state
      assertThat(extra1).isSameAs(extra1_toSpan);

      // we no longer have the same span ID, so we should decouple our baggage state
      TraceContext context3 = tracing.tracer().newChild(context1).context();
      ExtraBaggageFields extra3 = (ExtraBaggageFields) context3.extra().get(0);

      // we have different instances of extra
      assertThat(extra1).isNotSameAs(extra3);

      // however, the values inside are the same until a write occurs
      assertThat(extra1).isEqualTo(extra3);

      // check that the change is present, but the other contexts are the same
      String beforeUpdate = field1.getValue(context1);
      field1.updateValue(context1, "2");
      assertThat(extra1.getValue(field1)).isNotEqualTo(beforeUpdate); // copy-on-write
      assertThat(extra3.getValue(field1)).isEqualTo(beforeUpdate);
    }
  }

  /**
   * This scenario is possible, albeit rare. {@code tracer.nextSpan(extracted} } is called when
   * there is an implicit parent. For example, you have a trace in progress when extracting baggage
   * from an incoming message. Another example is where there is a span in scope due to a leak such
   * as from using {@link CurrentTraceContext.Default#inheritable()}.
   *
   * <p>Extracted baggage should merge into the current baggage state instead creating multiple
   * entries in {@link TraceContext#extra()}.
   */
  @Test public void nextSpanMergesExtraWithImplicitParent_hasvalue() {
    try (Tracing tracing = withNoopSpanReporter()) {
      ScopedSpan parent = tracing.tracer().startScopedSpan("parent");
      try {
        TraceContextOrSamplingFlags extracted = TraceContextOrSamplingFlags.newBuilder(EMPTY)
          .addExtra(factory.create())
          .build();

        field1.updateValue(parent.context(), value1);
        field1.updateValue(extracted, value2);
        field2.updateValue(extracted, value3);

        TraceContext context1 = tracing.tracer().nextSpan(extracted).context();

        assertThat(context1.extra()).hasSize(1); // merged

        assertThat(field1.getValue(context1)).isEqualTo(value2); // extracted should win!
        assertThat(field2.getValue(context1)).isEqualTo(value3);

        assertBaggageStateClaimed(context1.extra(), context1);
      } finally {
        parent.finish();
      }
    }
  }

  @Test public void nextSpanExtraWithImplicitParent_butNoImplicitBaggagevalue() {
    try (Tracing tracing = withNoopSpanReporter()) {

      ScopedSpan parent = tracing.tracer().startScopedSpan("parent");
      try {
        TraceContextOrSamplingFlags extracted = TraceContextOrSamplingFlags.newBuilder(EMPTY)
          .addExtra(factory.create())
          .build();

        field2.updateValue(extracted, value3);

        TraceContext context1 = tracing.tracer().nextSpan(extracted).context();

        assertThat(context1.extra()).hasSize(1); // merged

        assertThat(field2.getValue(context1)).isEqualTo(value3);

        assertBaggageStateClaimed(context1.extra(), context1);
      } finally {
        parent.finish();
      }
    }
  }

  @Test public void nextSpanExtraWithImplicitParent_butNoExtractedBaggagevalue() {
    try (Tracing tracing = withNoopSpanReporter()) {

      ScopedSpan parent = tracing.tracer().startScopedSpan("parent");
      try {
        field1.updateValue(parent.context(), value1);

        TraceContextOrSamplingFlags extracted = TraceContextOrSamplingFlags.create(EMPTY);

        // TODO didn't pass the reference from parent
        TraceContext context1 = tracing.tracer().nextSpan(extracted).context();

        assertThat(context1.extra()).hasSize(1); // didn't duplicate

        assertThat(field1.getValue(context1)).isEqualTo(value1);

        assertBaggageStateClaimed(context1.extra(), context1);
      } finally {
        parent.finish();
      }
    }
  }

  @Test public void idempotent() {
    List<Object> originalExtra = context.extra();
    assertThat(propagationFactory.decorate(context).extra())
      .isSameAs(originalExtra);
  }

  /** Ensures we don't accidentally use console logging, which is default */
  Tracing withNoopSpanReporter() {
    return Tracing.newBuilder()
      .currentTraceContext(StrictCurrentTraceContext.create())
      .spanReporter(Reporter.NOOP)
      .propagationFactory(propagationFactory)
      .build();
  }

  @Test public void decorate_makesNewExtra() {
    Map<List<Object>, List<Object>> inputToExpected = new LinkedHashMap<>();
    inputToExpected.put(asList(1L), asList(1L, factory.create()));
    inputToExpected.put(asList(1L, 2L), asList(1L, 2L, factory.create()));

    for (Entry<List<Object>, List<Object>> inputExpected : inputToExpected.entrySet()) {
      List<Object> actual = decorateAndReturnExtra(context, inputExpected.getKey());

      // doesn't drop the input elements
      assertThat(actual).containsAll(inputExpected.getKey());

      // adds a new propagation field container and claims it against the current context.
      assertBaggageStateClaimed(actual, context);
    }
  }

  @Test public void decorate_claimsFields() {
    Map<List<Object>, List<Object>> inputToExpected = new LinkedHashMap<>();
    inputToExpected.put(asList(factory.create()), asList(factory.create()));
    inputToExpected.put(asList(factory.create(), 1L), asList(factory.create(), 1L));
    inputToExpected.put(asList(1L, factory.create()), asList(1L, factory.create()));

    for (Entry<List<Object>, List<Object>> inputExpected : inputToExpected.entrySet()) {
      List<Object> actual = decorateAndReturnExtra(context, inputExpected.getKey());

      // Has the same elements
      assertThat(actual).isEqualTo(inputExpected.getKey());

      // The propagation fields are now claimed by this context
      assertBaggageStateClaimed(actual, context);
    }
  }

  @Test public void decorate_redundant() {
    ExtraBaggageFields alreadyClaimed = factory.create();
    factory.tryToClaim(alreadyClaimed, context.traceId(), context.spanId());

    Map<List<Object>, List<Object>> inputToExpected = new LinkedHashMap<>();
    inputToExpected.put(asList(alreadyClaimed), asList(factory.create()));
    inputToExpected.put(asList(alreadyClaimed, 1L), asList(factory.create(), 1L));
    inputToExpected.put(asList(1L, alreadyClaimed), asList(1L, factory.create()));

    for (Entry<List<Object>, List<Object>> inputExpected : inputToExpected.entrySet()) {
      List<Object> actual = decorateAndReturnExtra(context, inputExpected.getKey());

      // Verify no unexpected side effects
      assertThat(actual).isEqualTo(inputExpected.getKey());
      assertBaggageStateClaimed(actual, context);
    }
  }

  @Test public void decorate_forksWhenFieldsAlreadyClaimed() {
    TraceContext other = TraceContext.newBuilder().traceId(98L).spanId(99L).build();
    ExtraBaggageFields claimedByOther = factory.create();
    factory.tryToClaim(claimedByOther, other.traceId(), other.spanId());

    Map<List<Object>, List<Object>> inputToExpected = new LinkedHashMap<>();
    inputToExpected.put(asList(claimedByOther), asList(factory.create()));
    inputToExpected.put(asList(claimedByOther, 1L), asList(factory.create(), 1L));
    inputToExpected.put(asList(1L, claimedByOther), asList(1L, factory.create()));

    for (Entry<List<Object>, List<Object>> inputExpected : inputToExpected.entrySet()) {
      List<Object> actual = decorateAndReturnExtra(context, inputExpected.getKey());
      assertBaggageStateClaimed(actual, context);
      assertThat(actual).isEqualTo(inputExpected.getValue());
    }
  }

  @Test public void toSpan_selfLinksContext() {
    try (Tracing t = Tracing.newBuilder()
      .spanReporter(Reporter.NOOP)
      .currentTraceContext(StrictCurrentTraceContext.create())
      .propagationFactory(propagationFactory)
      .build()) {
      ScopedSpan parent = t.tracer().startScopedSpan("parent");
      try {
        assertBaggageStateClaimed(parent.context().extra(), parent.context());
      } finally {
        parent.finish();
      }
    }
  }

  void assertBaggageStateClaimed(List<Object> actual, TraceContext context) {
    assertThat(actual)
      .filteredOn(ExtraBaggageFields.class::isInstance)
      .hasSize(1)
      .flatExtracting("traceId", "spanId")
      .containsExactly(context.traceId(), context.spanId());
  }

  List<Object> decorateAndReturnExtra(TraceContext context, List<Object> key) {
    return factory.decorate(contextWithExtra(context, key)).extra();
  }

  static TraceContext contextWithExtra(TraceContext context, List<Object> extra) {
    return InternalPropagation.instance.withExtra(context, extra);
  }
}

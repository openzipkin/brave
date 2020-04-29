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
import brave.propagation.SamplingFlags;
import brave.propagation.StrictCurrentTraceContext;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.junit.Test;
import zipkin2.reporter.Reporter;

import static brave.internal.baggage.ExtraBaggageContext.findExtra;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class ExtraBaggageFieldsFactoryTest {
  BaggageField field1 = BaggageField.create("one");
  BaggageField field2 = BaggageField.create("two");
  String state1 = "1", state2 = "2", state3 = "3";

  ExtraBaggageFieldsFactory factory = new ExtraBaggageFieldsFactory(
    BaggageHandlers.string(field1),
    BaggageHandlers.string(field2)
  );

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
      putState(context1.extra(), field1, state1);

      TraceContext context2 = tracing.tracer().newChild(context1).context();

      // Instances are not the same
      assertThat(context1.findExtra(ExtraBaggageFields.class))
        .isNotSameAs(context2.findExtra(ExtraBaggageFields.class));

      // But have the same values
      assertThat(context1.findExtra(ExtraBaggageFields.class))
        .isEqualTo(context2.findExtra(ExtraBaggageFields.class));
      assertThat(getState(context1, field1))
        .isEqualTo(getState(context2, field1))
        .isEqualTo(state1);

      putState(context1.extra(), field1, state2);
      putState(context2.extra(), field1, state3);

      // Yet downstream changes don't affect eachother
      assertThat(getState(context1, field1)).isEqualTo(state2);
      assertThat(getState(context2, field1)).isEqualTo(state3);
    }
  }

  @Test public void contextsHaveIndependentState() {
    try (Tracing tracing = withNoopSpanReporter()) {

      TraceContext context1 = tracing.tracer().nextSpan().context();
      putState(context1.extra(), field1, state1);

      TraceContext context2 =
        tracing.tracer().toSpan(context1.toBuilder().sampled(false).build()).context();
      ExtraBaggageFields extraBaggageFields1 = (ExtraBaggageFields) context1.extra().get(0);
      ExtraBaggageFields extraBaggageFields1_toSpan = (ExtraBaggageFields) context2.extra().get(0);

      // we have the same span ID, so we should couple our propagation baggageState
      assertThat(extraBaggageFields1).isSameAs(extraBaggageFields1_toSpan);

      // we no longer have the same span ID, so we should decouple our propagation baggageState
      TraceContext context3 = tracing.tracer().newChild(context1).context();
      ExtraBaggageFields extraBaggageFields3 = (ExtraBaggageFields) context3.extra().get(0);

      // we have different instances of extra
      assertThat(extraBaggageFields1).isNotSameAs(extraBaggageFields3);

      // however, the values inside are the same until a write occurs
      assertThat(extraBaggageFields1).isEqualTo(extraBaggageFields3);

      // check that the change is present, but the other contexts are the same
      Object beforeUpdate = getState(context1, field1);
      field1.updateValue(context1, "2");
      assertThat(getState(extraBaggageFields1, field1)).isNotEqualTo(beforeUpdate); // copy-on-write
      assertThat(getState(extraBaggageFields3, field1)).isEqualTo(beforeUpdate);
    }
  }

  /**
   * This scenario is possible, albeit rare. {@code tracer.nextSpan(extracted} } is called when
   * there is an implicit parent. For example, you have a trace in progress when extracting trace
   * baggageState from an incoming message. Another example is where there is a span in scope due to
   * a leak such as from using {@link CurrentTraceContext.Default#inheritable()}.
   *
   * <p>When we are only extracting propagation baggageState, the baggageState should
   * merge as opposed to creating duplicate copies of {@link ExtraBaggageFields}.
   */
  @Test public void nextSpanMergesExtraWithImplicitParent_hasstate() {
    try (Tracing tracing = withNoopSpanReporter()) {
      ScopedSpan parent = tracing.tracer().startScopedSpan("parent");
      try {
        TraceContextOrSamplingFlags extracted = TraceContextOrSamplingFlags.newBuilder()
          .samplingFlags(SamplingFlags.EMPTY)
          .addExtra(factory.create())
          .build();

        putState(parent.context().extra(), field1, state1);
        putState(extracted.extra(), field1, state2);
        putState(extracted.extra(), field2, state3);

        TraceContext context1 = tracing.tracer().nextSpan(extracted).context();

        assertThat(context1.extra()).hasSize(1); // merged

        assertThat(getState(context1, field1)).isEqualTo(state2); // extracted should win!
        assertThat(getState(context1, field2)).isEqualTo(state3);

        assertBaggageStateClaimed(context1.extra(), context1);
      } finally {
        parent.finish();
      }
    }
  }

  @Test public void nextSpanExtraWithImplicitParent_butNoImplicitBaggagestate() {
    try (Tracing tracing = withNoopSpanReporter()) {

      ScopedSpan parent = tracing.tracer().startScopedSpan("parent");
      try {
        TraceContextOrSamplingFlags extracted = TraceContextOrSamplingFlags.newBuilder()
          .samplingFlags(SamplingFlags.EMPTY)
          .addExtra(factory.create())
          .build();

        putState(extracted.extra(), field2, state3);

        TraceContext context1 = tracing.tracer().nextSpan(extracted).context();

        assertThat(context1.extra()).hasSize(1); // merged

        assertThat(getState(context1, field2)).isEqualTo(state3);

        assertBaggageStateClaimed(context1.extra(), context1);
      } finally {
        parent.finish();
      }
    }
  }

  @Test public void nextSpanExtraWithImplicitParent_butNoExtractedBaggagestate() {
    try (Tracing tracing = withNoopSpanReporter()) {

      ScopedSpan parent = tracing.tracer().startScopedSpan("parent");
      try {
        putState(parent.context().extra(), field1, state1);

        TraceContextOrSamplingFlags extracted = TraceContextOrSamplingFlags.newBuilder()
          .samplingFlags(SamplingFlags.EMPTY)
          .build();

        // TODO didn't pass the reference from parent
        TraceContext context1 = tracing.tracer().nextSpan(extracted).context();

        assertThat(context1.extra()).hasSize(1); // didn't duplicate

        assertThat(getState(context1, field1)).isEqualTo(state1);

        assertBaggageStateClaimed(context1.extra(), context1);
      } finally {
        parent.finish();
      }
    }
  }

  @Test public void get_ignore_if_not_defined_index() {
    ExtraBaggageFields extraBaggageFields = factory.create();

    assertThat(extraBaggageFields.getState(4))
      .isNull();
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

  static void assertBaggageStateClaimed(List<Object> actual, TraceContext context) {
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

  void putState(List<Object> extra, BaggageField field, String state) {
    ExtraBaggageFields extraBaggageFields = findExtra(ExtraBaggageFields.class, extra);
    if (extraBaggageFields == null) return;
    putState(extraBaggageFields, field, state);
  }

  void putState(ExtraBaggageFields extraBaggageFields, BaggageField field, String state) {
    int index = extraBaggageFields.indexOf(field);
    if (index == -1) return;
    extraBaggageFields.putState(index, state);
  }

  String getState(TraceContext context, BaggageField field) {
    ExtraBaggageFields extraBaggageFields = context.findExtra(ExtraBaggageFields.class);
    if (extraBaggageFields == null) return null;
    return getState(extraBaggageFields, field);
  }

  String getState(ExtraBaggageFields extraBaggageFields, BaggageField field) {
    int index = extraBaggageFields.indexOf(field);
    return index != -1 ? (String) extraBaggageFields.getState(index) : null;
  }
}

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
import brave.propagation.B3Propagation;
import brave.propagation.CurrentTraceContext;
import brave.propagation.Propagation;
import brave.propagation.StrictCurrentTraceContext;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.List;
import org.junit.Test;
import zipkin2.reporter.Reporter;

import static brave.propagation.SamplingFlags.EMPTY;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class ExtraFactoryTest {
  BaggageField field1 = BaggageField.create("one");
  BaggageField field2 = BaggageField.create("two");
  String value1 = "1", value2 = "2", value3 = "3";

  // NOTE: while these tests check ExtraFactory, they are themselves coupled to BaggageFields
  BaggageFieldsFactory factory = BaggageFieldsFactory.create(asList(field1, field2), false);
  Class<? extends Extra> extraClass = BaggageFields.class;

  Propagation.Factory propagationFactory = new Propagation.Factory() {
    @Override public <K> Propagation<K> create(Propagation.KeyFactory<K> keyFactory) {
      return B3Propagation.FACTORY.create(keyFactory);
    }

    @Override public TraceContext decorate(TraceContext context) {
      return factory.decorate(context);
    }
  };

  TraceContext context = TraceContext.newBuilder().traceId(1L).spanId(2L).sampled(true).build();
  TraceContext decorated = propagationFactory.decorate(context);

  @Test public void contextsAreIndependent() {
    try (Tracing tracing = withNoopSpanReporter()) {
      TraceContext context1 = tracing.tracer().nextSpan().context();
      field1.updateValue(context1, value1);

      TraceContext context2 = tracing.tracer().newChild(context1).context();

      // Instances are not the same
      assertThat(context1.findExtra(extraClass))
        .isNotSameAs(context2.findExtra(extraClass));

      // But have the same values
      assertThat(context1.findExtra(extraClass))
        .isEqualTo(context2.findExtra(extraClass));
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
      Extra extra1 = (Extra) context1.extra().get(0);
      Extra extra1_toSpan = (Extra) context2.extra().get(0);

      // we have the same span ID, so we should couple our baggage state
      assertThat(extra1).isSameAs(extra1_toSpan);

      // we no longer have the same span ID, so we should decouple our baggage state
      TraceContext context3 = tracing.tracer().newChild(context1).context();
      Extra extra3 = (Extra) context3.extra().get(0);

      // we have different instances of extra
      assertThat(extra1).isNotSameAs(extra3);

      // however, the values inside are the same until a write occurs
      assertThat(extra1).isEqualTo(extra3);

      // check that the change is present, but the other contexts are the same
      String beforeUpdate = field1.getValue(context1);
      field1.updateValue(context1, "2");

      assertThat(field1.getValue(context1)).isNotEqualTo(beforeUpdate); // copy-on-write
      assertThat(field1.getValue(context3)).isEqualTo(beforeUpdate);
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
  @Test public void nextSpanMergesExtraWithImplicitParent_hasValue() {
    try (Tracing tracing = withNoopSpanReporter()) {
      ScopedSpan parent = tracing.tracer().startScopedSpan("parent");
      try {
        TraceContextOrSamplingFlags extracted = TraceContextOrSamplingFlags.EMPTY.toBuilder()
            .addExtra(factory.create())
            .build();

        field1.updateValue(parent.context(), value1);
        field1.updateValue(extracted, value2);
        field2.updateValue(extracted, value3);

        TraceContext context1 = tracing.tracer().nextSpan(extracted).context();

        assertThat(context1.extra()).hasSize(1); // merged

        assertThat(field1.getValue(context1)).isEqualTo(value2); // extracted should win!
        assertThat(field2.getValue(context1)).isEqualTo(value3);

        assertExtraClaimed(context1);
      } finally {
        parent.finish();
      }
    }
  }

  @Test public void nextSpanExtraWithImplicitParent_butNoImplicitBaggagevalue() {
    try (Tracing tracing = withNoopSpanReporter()) {

      ScopedSpan parent = tracing.tracer().startScopedSpan("parent");
      try {
        TraceContextOrSamplingFlags extracted = TraceContextOrSamplingFlags.EMPTY.toBuilder()
            .addExtra(factory.create())
            .build();

        field2.updateValue(extracted, value3);

        TraceContext context1 = tracing.tracer().nextSpan(extracted).context();

        assertThat(context1.extra()).hasSize(1); // merged

        assertThat(field2.getValue(context1)).isEqualTo(value3);

        assertExtraClaimed(context1);
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

        assertExtraClaimed(context1);
      } finally {
        parent.finish();
      }
    }
  }

  @Test public void idempotent() {
    List<Object> originalExtra = decorated.extra();
    assertThat(propagationFactory.decorate(decorated).extra())
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

  @Test public void ensureContainsExtra_makesNewExtra() {
    List<TraceContext> contexts = asList(
        context.toBuilder().build(),
        context.toBuilder().addExtra(1L).build(),
        context.toBuilder().addExtra(1L).addExtra(2L).build()
    );

    for (TraceContext context : contexts) {
      // adds a new extra container and claims it against the current context.
      TraceContext ensured = factory.decorate(context);

      assertThat(ensured.extra())
          .hasSize(context.extra().size() + 1)
          .containsAll(context.extra());
      assertExtraClaimed(ensured);
    }
  }

  @Test public void ensureContainsExtra_claimsFields() {
    List<TraceContext> contexts = asList(
        context.toBuilder().addExtra(factory.create()).build(),
        context.toBuilder().addExtra(1L).addExtra(factory.create()).build(),
        context.toBuilder().addExtra(factory.create()).addExtra(1L).build(),
        context.toBuilder().addExtra(1L).addExtra(factory.create()).addExtra(2L).build()
    );

    for (TraceContext context : contexts) {
      // re-uses an extra container and claims it against the current context.
      TraceContext ensured = factory.decorate(context);

      assertThat(ensured.extra()).isSameAs(context.extra());
      assertExtraClaimed(ensured);
    }
  }

  @Test public void ensureContainsExtra_redundant() {
    List<TraceContext> contexts = asList(
        context.toBuilder().addExtra(factory.create()).build(),
        context.toBuilder().addExtra(1L).addExtra(factory.create()).build(),
        context.toBuilder().addExtra(factory.create()).addExtra(1L).build(),
        context.toBuilder().addExtra(1L).addExtra(factory.create()).addExtra(2L).build()
    );

    for (TraceContext context : contexts) {
      context = factory.decorate(context);

      assertThat(factory.decorate(context)).isSameAs(context);
    }
  }

  @Test public void decorate_returnsInputOnCreateNull() {
    BadFactory badFactory = new BadFactory();
    assertThat(badFactory.create()).isNull(); // sanity check

    assertThat(badFactory.decorate(context))
        .isSameAs(context);
  }

  static abstract class BadExtra extends Extra<BadExtra, BadFactory> {
    BadExtra(BadFactory factory) {
      super(factory);
    }
  }

  static final class BadFactory extends ExtraFactory<BadExtra, BadFactory> {
    BadFactory() {
      super(new Object());
    }

    @Override protected BadExtra create() {
      return null;
    }
  }

  /** Logs instead of crashing on bad usage */
  @Test public void decorate_returnsInputOnRedundantExtra() {
    context = context.toBuilder()
        .addExtra(factory.create())
        .addExtra(factory.create())
        .addExtra(factory.create())
        .build();

    assertThat(factory.decorate(context))
        .isSameAs(context);
  }

  @Test public void ensureContainsExtra_forksWhenFieldsAlreadyClaimed() {
    TraceContext other = TraceContext.newBuilder().traceId(98L).spanId(99L).build();
    Extra claimed = factory.decorate(other).findExtra(extraClass);

    List<TraceContext> contexts = asList(
        context.toBuilder().addExtra(claimed).build(),
        context.toBuilder().addExtra(1L).addExtra(claimed).build(),
        context.toBuilder().addExtra(claimed).addExtra(1L).build(),
        context.toBuilder().addExtra(1L).addExtra(claimed).addExtra(2L).build()
    );

    for (TraceContext context : contexts) {
      TraceContext ensured = factory.decorate(context);

      assertThat(ensured).isNotSameAs(context);
      assertThat(ensured.extra())
          .isNotSameAs(context.extra())
          .hasSize(context.extra().size());
      assertExtraClaimed(ensured);
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
        assertExtraClaimed(parent.context());
      } finally {
        parent.finish();
      }
    }
  }

  void assertExtraClaimed(TraceContext context) {
    assertThat(context.extra())
      .filteredOn(Extra.class::isInstance)
      .hasSize(1)
      .flatExtracting("traceId", "spanId")
      .containsExactly(context.traceId(), context.spanId());
  }
}

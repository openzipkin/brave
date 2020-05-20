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
package brave.internal.extra;

import brave.propagation.B3Propagation;
import brave.propagation.CurrentTraceContext;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import java.util.List;
import org.junit.Test;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class ExtraFactoryTest {
  BasicMapExtra.Factory factory = new BasicMapExtra.FactoryBuilder()
      .addInitialKey("1")
      .addInitialKey("2")
      .build();

  Propagation.Factory propagationFactory = new Propagation.Factory() {
    @Deprecated @Override public <K> Propagation<K> create(Propagation.KeyFactory<K> keyFactory) {
      return B3Propagation.FACTORY.create(keyFactory);
    }

    @Override public TraceContext decorate(TraceContext context) {
      return factory.decorate(context);
    }
  };

  TraceContext context = TraceContext.newBuilder().traceId(1L).spanId(2L).sampled(true).build();
  TraceContext context2 = context.toBuilder().parentId(2L).spanId(3L).build();

  @Test public void contextsAreIndependent() {
    TraceContext decorated = propagationFactory.decorate(context);
    BasicMapExtra extra1 = decorated.findExtra(BasicMapExtra.class);
    extra1.put("1", "one");

    context2 = propagationFactory.decorate(context2.toBuilder().addExtra(extra1).build());
    BasicMapExtra extra2 = context2.findExtra(BasicMapExtra.class);

    // Instances are not the same
    assertThat(extra1).isNotSameAs(extra2);

    // But have the same values
    assertThat(extra1).isEqualTo(extra2);

    extra1.put("1", "two");
    extra2.put("1", "three");

    // Yet downstream changes don't affect eachother
    assertThat(extra1.get("1")).isEqualTo("two");
    assertThat(extra2.get("1")).isEqualTo("three");
  }

  @Test public void contextsHaveIndependentValue() {
    TraceContext decorated = propagationFactory.decorate(context);
    BasicMapExtra extra1 = decorated.findExtra(BasicMapExtra.class);
    extra1.put("1", "two");

    // we have the same span ID, so we should couple our extra state
    assertThat(propagationFactory.decorate(decorated.toBuilder().build()).extra())
        .isEqualTo(decorated.extra());

    // we no longer have the same span ID, so we should decouple our extra state
    context2 = propagationFactory.decorate(context2.toBuilder().addExtra(extra1).build());
    BasicMapExtra extra2 = context2.findExtra(BasicMapExtra.class);

    // we have different instances of extra
    assertThat(extra1).isNotSameAs(extra2);

    // however, the values inside are the same until a write occurs
    assertThat(extra1).isEqualTo(extra2);

    // check that the change is present, but the other contexts are the same
    String beforeUpdate = extra1.get("1");
    extra1.put("1", "2");

    assertThat(extra1.get("1")).isNotEqualTo(beforeUpdate); // copy-on-write
    assertThat(extra2.get("1")).isEqualTo(beforeUpdate);
  }

  /**
   * This scenario is possible, albeit rare. {@code tracer.nextSpan(extracted} } is called when
   * there is an implicit parent. For example, you have a trace in progress when extracting extra
   * from an incoming message. Another example is where there is a span in scope due to a leak such
   * as from using {@link CurrentTraceContext.Default#inheritable()}.
   *
   * <p>Extracted extra should merge into the current extra state instead creating multiple
   * entries in {@link TraceContext#extra()}.
   */
  @Test public void decorate_extractedExtra_plus_parent_merge() {
    TraceContext decorated = propagationFactory.decorate(context);
    BasicMapExtra extra1 = decorated.findExtra(BasicMapExtra.class);

    BasicMapExtra extracted = factory.create();
    extra1.put("1", "one");
    extracted.put("1", "two");
    extracted.put("2", "three");

    context2 = propagationFactory.decorate(
        context2.toBuilder().addExtra(extra1).addExtra(extracted).build());
    BasicMapExtra extra2 = context2.findExtra(BasicMapExtra.class);
    assertThat(context2.extra()).containsExactly(extra2); // merged

    assertThat(extra2.get("1")).isEqualTo("two"); // extracted should win!
    assertThat(extra2.get("2")).isEqualTo("three");

    assertExtraClaimed(context2);
  }

  @Test public void decorate_extractedExtra_plus_emptyParent() {
    TraceContext decorated = propagationFactory.decorate(context);
    BasicMapExtra extra1 = decorated.findExtra(BasicMapExtra.class);

    BasicMapExtra extracted = factory.create();
    extracted.put("2", "three");

    context2 = propagationFactory.decorate(
        context2.toBuilder().addExtra(extra1).addExtra(extracted).build());
    BasicMapExtra extra2 = context2.findExtra(BasicMapExtra.class);
    assertThat(context2.extra()).containsExactly(extra2); // merged

    assertThat(extra2.get("2")).isEqualTo("three");

    assertExtraClaimed(context2);
  }

  @Test public void decorate_parent() {
    TraceContext decorated = propagationFactory.decorate(context);
    BasicMapExtra extra1 = decorated.findExtra(BasicMapExtra.class);
    extra1.put("1", "one");

    context2 = propagationFactory.decorate(context2.toBuilder().addExtra(extra1).build());
    BasicMapExtra extra2 = context2.findExtra(BasicMapExtra.class);
    assertThat(context2.extra()).containsExactly(extra2); // didn't duplicate

    assertThat(extra2.get("1")).isEqualTo("one");

    assertExtraClaimed(context2);
  }

  @Test public void idempotent() {
    TraceContext decorated = propagationFactory.decorate(context);
    List<Object> originalExtra = decorated.extra();
    assertThat(propagationFactory.decorate(decorated).extra())
        .isSameAs(originalExtra);
  }

  @Test public void decorate_makesNewExtra() {
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

  @Test public void decorate_claimsFields() {
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

  @Test public void decorate_redundant() {
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

  @Test public void decorate_forksWhenFieldsAlreadyClaimed() {
    TraceContext other = TraceContext.newBuilder().traceId(98L).spanId(99L).build();
    BasicMapExtra claimed = factory.decorate(other).findExtra(BasicMapExtra.class);

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

  @Test public void decorate_claimsContext() {
    assertExtraClaimed(propagationFactory.decorate(context));
  }

  void assertExtraClaimed(TraceContext context) {
    assertThat(context.extra())
      .filteredOn(Extra.class::isInstance)
      .hasSize(1)
      .flatExtracting("traceId", "spanId")
      .containsExactly(context.traceId(), context.spanId());
  }
}

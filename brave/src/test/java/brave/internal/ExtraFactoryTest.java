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
package brave.internal;

import brave.ScopedSpan;
import brave.Tracing;
import brave.propagation.B3Propagation;
import brave.propagation.Propagation;
import brave.propagation.StrictCurrentTraceContext;
import brave.propagation.TraceContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.junit.Before;
import org.junit.Test;
import zipkin2.reporter.Reporter;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class ExtraFactoryTest<E, F extends ExtraFactory<E>> {
  protected F factory;
  protected Propagation.Factory propagationFactory;
  protected TraceContext context;

  protected abstract F newFactory();

  @Before public void setup() {
    factory = newFactory();
    propagationFactory = new Propagation.Factory() {
      @Override public <K> Propagation<K> create(Propagation.KeyFactory<K> keyFactory) {
        return B3Propagation.FACTORY.create(keyFactory);
      }

      @Override public TraceContext decorate(TraceContext context) {
        return factory.decorate(context);
      }
    };
    context = propagationFactory.decorate(TraceContext.newBuilder()
      .traceId(1L)
      .spanId(2L)
      .sampled(true)
      .build());
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
      assertPropagationFieldsClaimedBy(actual, context);
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
      assertPropagationFieldsClaimedBy(actual, context);
    }
  }

  @Test public void decorate_redundant() {
    E alreadyClaimed = factory.create();
    factory.tryToClaim(alreadyClaimed, context.traceId(), context.spanId());

    Map<List<Object>, List<Object>> inputToExpected = new LinkedHashMap<>();
    inputToExpected.put(asList(alreadyClaimed), asList(factory.create()));
    inputToExpected.put(asList(alreadyClaimed, 1L), asList(factory.create(), 1L));
    inputToExpected.put(asList(1L, alreadyClaimed), asList(1L, factory.create()));

    for (Entry<List<Object>, List<Object>> inputExpected : inputToExpected.entrySet()) {
      List<Object> actual = decorateAndReturnExtra(context, inputExpected.getKey());

      // Verify no unexpected side effects
      assertThat(actual).isEqualTo(inputExpected.getKey());
      assertPropagationFieldsClaimedBy(actual, context);
    }
  }

  @Test public void decorate_forksWhenFieldsAlreadyClaimed() {
    TraceContext other = TraceContext.newBuilder().traceId(98L).spanId(99L).build();
    E claimedByOther = factory.create();
    factory.tryToClaim(claimedByOther, other.traceId(), other.spanId());

    Map<List<Object>, List<Object>> inputToExpected = new LinkedHashMap<>();
    inputToExpected.put(asList(claimedByOther), asList(factory.create()));
    inputToExpected.put(asList(claimedByOther, 1L), asList(factory.create(), 1L));
    inputToExpected.put(asList(1L, claimedByOther), asList(1L, factory.create()));

    for (Entry<List<Object>, List<Object>> inputExpected : inputToExpected.entrySet()) {
      List<Object> actual = decorateAndReturnExtra(context, inputExpected.getKey());
      assertPropagationFieldsClaimedBy(actual, context);
      assertEquivalentExtraIgnoringIds(actual, inputExpected.getValue());
    }
  }

  static void assertPropagationFieldsClaimedBy(List<Object> actual, TraceContext context) {
    assertThat(actual)
      .filteredOn(PropagationFields.class::isInstance)
      .hasSize(1)
      .flatExtracting("traceId", "spanId")
      .containsExactly(context.traceId(), context.spanId());
  }

  static void assertEquivalentExtraIgnoringIds(List<Object> actual, List<Object> expected) {
    assertThat(actual)
      .usingFieldByFieldElementComparator()
      .usingElementComparatorIgnoringFields("traceId", "spanId")
      .containsExactlyElementsOf(expected);
  }

  @Test public void idempotent() {
    List<Object> originalExtra = context.extra();
    assertThat(propagationFactory.decorate(context).extra())
      .isSameAs(originalExtra);
  }

  @Test public void toSpan_selfLinksContext() {
    try (Tracing t = Tracing.newBuilder()
      .spanReporter(Reporter.NOOP)
      .currentTraceContext(StrictCurrentTraceContext.create())
      .propagationFactory(propagationFactory)
      .build()) {
      ScopedSpan parent = t.tracer().startScopedSpan("parent");
      try {
        E extra = (E) parent.context().extra().get(0);

        assertAssociatedWith(extra, parent.context().traceId(), parent.context().spanId());
      } finally {
        parent.finish();
      }
    }
  }

  protected abstract void assertAssociatedWith(E extra, long traceId, long spanId);

  List<Object> decorateAndReturnExtra(TraceContext context, List<Object> key) {
    return factory.decorate(contextWithExtra(context, key)).extra();
  }

  static TraceContext contextWithExtra(TraceContext context, List<Object> extra) {
    return InternalPropagation.instance.withExtra(context, extra);
  }
}

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
package brave.baggage;

import brave.baggage.BaggagePropagationConfig.SingleBaggageField;
import brave.baggage.CorrelationScopeConfig.SingleCorrelationField;
import brave.baggage.CorrelationUpdateScope.Single;
import brave.internal.CorrelationContext;
import brave.internal.Nullable;
import brave.propagation.B3Propagation;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.CurrentTraceContext.ScopeDecorator;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;

public class CorrelationScopeDecoratorTest {
  static final SingleCorrelationField
    TRACE_ID =
    SingleCorrelationField.newBuilder(BaggageFields.TRACE_ID).name("X-B3-TraceId").build(),
    FIELD = SingleCorrelationField.create(BaggageField.create("userId")),
    FIELD_2 = SingleCorrelationField.create(BaggageField.create("country-code")),
    DIRTY_FIELD = FIELD.toBuilder().name("dirty").dirty().build(),
    LOCAL_FIELD = SingleCorrelationField.create(BaggageField.create("serviceId")),
    FLUSH_FIELD = SingleCorrelationField.newBuilder(BaggageField.create("bp"))
      .name("flushed")
      .flushOnUpdate()
      .build();
  static final Map<String, String> map = new LinkedHashMap<>();

  Propagation.Factory baggageFactory = BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY)
    .add(SingleBaggageField.local(LOCAL_FIELD.baggageField()))
    .add(SingleBaggageField.remote(FIELD.baggageField()))
    .add(SingleBaggageField.remote(FIELD_2.baggageField()))
    .add(SingleBaggageField.remote(FLUSH_FIELD.baggageField()))
    .build();

  TraceContext context = TraceContext.newBuilder()
    .traceId(1L)
    .parentId(2L)
    .spanId(3L)
    .sampled(true)
    .build();
  TraceContext contextWithBaggage = baggageFactory.decorate(context);

  ScopeDecorator decorator = new TestBuilder().build();
  ScopeDecorator onlyTraceIdDecorator = new TestBuilder()
    .clear()
    .add(TRACE_ID)
    .build();
  ScopeDecorator onlyScopeDecorator = new TestBuilder()
    .clear()
    .add(FIELD)
    .build();
  ScopeDecorator withBaggageFieldsDecorator = new TestBuilder()
    .clear()
    .add(TRACE_ID)
    .add(FIELD)
    .add(LOCAL_FIELD)
    .add(FIELD_2)
    .build();
  ScopeDecorator withFlushOnUpdateScopeDecorator = new TestBuilder()
    .add(FIELD)
    .add(LOCAL_FIELD)
    .add(FIELD_2)
    .add(FLUSH_FIELD)
    .build();
  ScopeDecorator onlyFlushOnUpdateScopeDecorator = new TestBuilder()
    .clear()
    .add(FLUSH_FIELD)
    .build();
  ScopeDecorator withDirtyFieldDecorator = new TestBuilder()
    .add(DIRTY_FIELD)
    .build();
  ScopeDecorator onlyDirtyFieldDecorator = new TestBuilder()
    .clear()
    .add(DIRTY_FIELD)
    .build();

  @Before public void before() {
    map.clear();
  }

  @After public void assertClear() {
    assertThat(map).isEmpty();
  }

  @Test public void no_dupes() {
    CorrelationScopeDecorator.Builder builder =
      new TestBuilder().add(FLUSH_FIELD);

    assertThatThrownBy(() -> builder.add(FLUSH_FIELD))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Baggage Field already added: bp");
  }

  @Test public void clear_and_add() {
    CorrelationScopeDecorator.Builder builder = new TestBuilder()
      .add(FIELD)
      .add(FLUSH_FIELD);

    Set<CorrelationScopeConfig> fields = builder.configs();

    builder.clear();

    fields.forEach(builder::add);

    assertThat(builder)
      .usingRecursiveComparison()
      .isEqualTo(new TestBuilder()
        .add(FIELD)
        .add(FLUSH_FIELD));
  }

  @Test public void doesntDecorateNoop() {
    assertThat(decorator.decorateScope(contextWithBaggage, Scope.NOOP)).isSameAs(Scope.NOOP);
    assertThat(decorator.decorateScope(null, Scope.NOOP)).isSameAs(Scope.NOOP);
  }

  @Test public void shouldRevertDirtyFields() {
    Single scopeOne =
      (Single) onlyDirtyFieldDecorator.decorateScope(contextWithBaggage, Scope.NOOP);
    assertThat(scopeOne.shouldRevert).isTrue();
    scopeOne.close();

    CorrelationUpdateScope.Multiple scopeMultiple =
      (CorrelationUpdateScope.Multiple) withDirtyFieldDecorator.decorateScope(contextWithBaggage, Scope.NOOP);

    BitSet shouldRevert = BitSet.valueOf(new long[] {scopeMultiple.shouldRevert});
    assertThat(scopeMultiple.fields).extracting(SingleCorrelationField::name)
      .containsExactly("traceId", "spanId", "dirty");

    assertThat(shouldRevert.get(0)).isFalse();
    assertThat(shouldRevert.get(1)).isFalse();
    assertThat(shouldRevert.get(2)).isTrue();
    scopeMultiple.close();
  }

  @Test public void flushOnUpdateFieldsMatchScope() {
    CorrelationScopeDecorator.Single decoratorOne =
      (CorrelationScopeDecorator.Single) onlyFlushOnUpdateScopeDecorator;
    assertThat(decoratorOne.field.flushOnUpdate()).isTrue();

    try (Scope scope = decoratorOne.decorateScope(contextWithBaggage, Scope.NOOP)) {
      assertThat(scope).isInstanceOf(CorrelationFlushScope.class);
    }

    CorrelationScopeDecorator.Multiple decoratorMultiple =
      (CorrelationScopeDecorator.Multiple) withFlushOnUpdateScopeDecorator;

    try (Scope scope = decoratorMultiple.decorateScope(contextWithBaggage, Scope.NOOP)) {
      assertThat(scope).isInstanceOf(CorrelationFlushScope.class);
    }
  }

  @Test public void decoratesNoop_matchingDirtyField() {
    FIELD.baggageField().updateValue(contextWithBaggage, "romeo");
    map.put("dirty", "romeo");

    decoratesNoop_dirtyField();
    map.clear();
  }

  @Test public void decoratesNoop_matchingNullDirtyField() {
    decoratesNoop_dirtyField();
  }

  /**
   * All dirty fields should be reverted at the end of the scope, because end-users can interfere
   * with the underlying context in the middle of the scope. (ex. MDC.put)
   */
  void decoratesNoop_dirtyField() {
    try (Scope scope = withDirtyFieldDecorator.decorateScope(contextWithBaggage, Scope.NOOP)) {
      assertThat(scope).isNotSameAs(Scope.NOOP);
    }
    try (Scope scope = onlyDirtyFieldDecorator.decorateScope(contextWithBaggage, Scope.NOOP)) {
      assertThat(scope).isNotSameAs(Scope.NOOP);
    }
  }

  /** Fields that don't flush inside a s have no value and no value of the underlying context. */
  @Test public void doesntDecorateNoop_matchingNullBaggageField() {
    assertThat(onlyTraceIdDecorator.decorateScope(contextWithBaggage, Scope.NOOP)).isSameAs(Scope.NOOP);
    assertThat(withBaggageFieldsDecorator.decorateScope(contextWithBaggage, Scope.NOOP)).isSameAs(Scope.NOOP);
    assertThat(onlyScopeDecorator.decorateScope(contextWithBaggage, Scope.NOOP)).isSameAs(Scope.NOOP);
  }

  /** Even when values match, FlushOnUpdate fields can update later, so NOOP can't be used. */
  @Test public void decoratesNoop_matchingNullFlushOnUpdateBaggageField() {
    try (Scope scope = onlyFlushOnUpdateScopeDecorator.decorateScope(contextWithBaggage, Scope.NOOP)) {
      assertThat(scope).isNotSameAs(Scope.NOOP);
    }
    try (Scope scope = withFlushOnUpdateScopeDecorator.decorateScope(contextWithBaggage, Scope.NOOP)) {
      assertThat(scope).isNotSameAs(Scope.NOOP);
    }
  }

  /** Fields that don't flush inside a s match the values of the underlying context. */
  @Test public void doesntDecorateNoop_matchingBaggageField() {
    FIELD.baggageField().updateValue(contextWithBaggage, "romeo");
    FIELD_2.baggageField().updateValue(contextWithBaggage, "FO");
    LOCAL_FIELD.baggageField().updateValue(contextWithBaggage, "abcd");
    map.put(FIELD.name(), "romeo");
    map.put(FIELD_2.name(), "FO");
    map.put(LOCAL_FIELD.name(), "abcd");

    assertThat(onlyTraceIdDecorator.decorateScope(contextWithBaggage, Scope.NOOP)).isSameAs(Scope.NOOP);
    assertThat(withBaggageFieldsDecorator.decorateScope(contextWithBaggage, Scope.NOOP)).isSameAs(Scope.NOOP);
    assertThat(onlyScopeDecorator.decorateScope(contextWithBaggage, Scope.NOOP)).isSameAs(Scope.NOOP);
    map.clear();
  }

  /** When a context is in an unexpected state, save off fields and revert. */
  @Test public void decoratesNoop_unconfiguredFields() {
    for (ScopeDecorator decorator : asList(withBaggageFieldsDecorator, onlyScopeDecorator)) {
      map.put(FIELD.name(), "romeo");
      map.put(FIELD_2.name(), "FO");
      map.put(LOCAL_FIELD.name(), "abcd");

      try (Scope scope = decorator.decorateScope(context, Scope.NOOP)) {
        assertThat(scope).isNotSameAs(Scope.NOOP);
      }
    }
    map.clear();
  }

  @Test public void doesntRevertMultipleTimes_singleField() {
    FIELD.baggageField().updateValue(contextWithBaggage, "romeo");
    map.put(FIELD.name(), "romeo");

    try (Scope s = onlyScopeDecorator.decorateScope(null, Scope.NOOP)) {
      assertThat(map).isEmpty();
      s.close();
      assertThat(map).isNotEmpty();
      map.clear();

      s.close();
      assertThat(map).isEmpty(); // didn't revert again
    }

    map.put("flushed", "excel");
    FLUSH_FIELD.baggageField().updateValue(contextWithBaggage, "excel");

    try (Scope s = onlyFlushOnUpdateScopeDecorator.decorateScope(null, mock(Scope.class))) {
      assertThat(map).isEmpty();
      s.close();
      assertThat(map).isNotEmpty();
      map.clear();

      s.close();
      assertThat(map).isEmpty(); // didn't revert again
    }
  }

  @Test public void doesntRevertMultipleTimes_multipleFields() {
    FIELD.baggageField().updateValue(contextWithBaggage, "romeo");
    FIELD_2.baggageField().updateValue(contextWithBaggage, "FO");
    LOCAL_FIELD.baggageField().updateValue(contextWithBaggage, "abcd");
    map.put(FIELD_2.name(), "FO");
    map.put(LOCAL_FIELD.name(), "abcd");
    map.put(FIELD.name(), "romeo");

    try (Scope s = withBaggageFieldsDecorator.decorateScope(null, Scope.NOOP)) {
      assertThat(map).isEmpty();
      s.close();
      assertThat(map).isNotEmpty();
      map.clear();

      s.close();
      assertThat(map).isEmpty(); // didn't revert again
    }

    map.put("flushed", "excel");
    FLUSH_FIELD.baggageField().updateValue(contextWithBaggage, "excel");

    try (Scope s = withFlushOnUpdateScopeDecorator.decorateScope(null, mock(Scope.class))) {
      assertThat(map).isEmpty();
      s.close();
      assertThat(map).isNotEmpty();
      map.clear();

      s.close();
      assertThat(map).isEmpty(); // didn't revert again
    }
  }

  @Test public void decoratesNoop_nullMeansClear() {
    FIELD.baggageField().updateValue(context, "romeo");
    FIELD_2.baggageField().updateValue(context, "FO");
    LOCAL_FIELD.baggageField().updateValue(context, "abcd");
    FLUSH_FIELD.baggageField().updateValue(context, "excel");
    map.put(FIELD.name(), "romeo");
    map.put(FIELD_2.name(), "FO");
    map.put(LOCAL_FIELD.name(), "abcd");

    try (Scope s = withBaggageFieldsDecorator.decorateScope(null, Scope.NOOP)) {
      assertThat(map).isEmpty();
    }

    try (Scope s = onlyScopeDecorator.decorateScope(null, Scope.NOOP)) {
      assertThat(map).doesNotContainKey(FIELD.name());
    }

    FLUSH_FIELD.baggageField().updateValue(contextWithBaggage, "excel");
    map.put("flushed", "excel");

    try (Scope s = withFlushOnUpdateScopeDecorator.decorateScope(null, Scope.NOOP)) {
      assertThat(map).isEmpty();
    }

    try (Scope s = onlyFlushOnUpdateScopeDecorator.decorateScope(null, Scope.NOOP)) {
      assertThat(map).doesNotContainKey("flushed");
    }
    map.clear();
  }

  @Test public void addsAndRemoves() {
    try (Scope s = decorator.decorateScope(contextWithBaggage, mock(Scope.class))) {
      assertThat(map).containsOnly(
        entry("traceId", "0000000000000001"),
        entry("spanId", "0000000000000003")
      );
    }
    assertThat(map.isEmpty());
  }

  @Test public void addsAndRemoves_onlyTraceId() {
    try (Scope s = onlyTraceIdDecorator.decorateScope(contextWithBaggage, mock(Scope.class))) {
      assertThat(map).containsOnly(entry("X-B3-TraceId", "0000000000000001"));
    }
    assertThat(map.isEmpty());
  }

  @Test public void addsAndRemoves_onlyBaggageField() {
    FIELD.baggageField().updateValue(contextWithBaggage, "romeo");
    try (Scope s = onlyScopeDecorator.decorateScope(contextWithBaggage, mock(Scope.class))) {
      assertThat(map).containsOnly(entry(FIELD.name(), "romeo"));
    }
    assertThat(map.isEmpty());

    FLUSH_FIELD.baggageField().updateValue(contextWithBaggage, "excel");
    try (
      Scope s = onlyFlushOnUpdateScopeDecorator.decorateScope(contextWithBaggage, mock(Scope.class))) {
      assertThat(map).containsOnly(entry("flushed", "excel"));
    }

    assertThat(map.isEmpty());
  }

  @Test public void addsAndRemoves_withMultipleBaggageField() {
    FIELD.baggageField().updateValue(contextWithBaggage, "romeo");
    FIELD_2.baggageField().updateValue(contextWithBaggage, "FO");
    LOCAL_FIELD.baggageField().updateValue(contextWithBaggage, "abcd");

    try (Scope s = withBaggageFieldsDecorator.decorateScope(contextWithBaggage, mock(Scope.class))) {
      assertThat(map).containsOnly(
        entry("X-B3-TraceId", "0000000000000001"),
        entry(FIELD.name(), "romeo"),
        entry(FIELD_2.name(), "FO"),
        entry(LOCAL_FIELD.name(), "abcd")
      );
    }
    assertThat(map.isEmpty());

    FLUSH_FIELD.baggageField().updateValue(contextWithBaggage, "excel");
    try (
      Scope s = withFlushOnUpdateScopeDecorator.decorateScope(contextWithBaggage, mock(Scope.class))) {
      assertThat(map).containsOnly(
        entry("traceId", "0000000000000001"),
        entry("spanId", "0000000000000003"),
        entry(FIELD.name(), "romeo"),
        entry(FIELD_2.name(), "FO"),
        entry(LOCAL_FIELD.name(), "abcd"),
        entry("flushed", "excel")
      );
    }
    assertThat(map.isEmpty());
  }

  @Test public void revertsChanges() {
    map.put("traceId", "000000000000000a");
    map.put("spanId", "000000000000000c");
    Map<String, String> snapshot = new LinkedHashMap<>(map);

    try (Scope s = decorator.decorateScope(contextWithBaggage, mock(Scope.class))) {
      assertThat(map).containsOnly(
        entry("traceId", "0000000000000001"),
        entry("spanId", "0000000000000003")
      );
    }

    assertThat(map).isEqualTo(snapshot);
    map.clear();
  }

  @Test public void revertsChanges_onlyTraceId() {
    map.put("X-B3-TraceId", "000000000000000a");
    Map<String, String> snapshot = new LinkedHashMap<>(map);

    try (Scope s = onlyTraceIdDecorator.decorateScope(contextWithBaggage, mock(Scope.class))) {
      assertThat(map).containsOnly(entry("X-B3-TraceId", "0000000000000001"));
    }

    assertThat(map).isEqualTo(snapshot);
    map.clear();
  }

  @Test public void revertsChanges_onlyBaggageField() {
    map.put(FIELD.name(), "bob");
    Map<String, String> snapshot = new LinkedHashMap<>(map);

    FIELD.baggageField().updateValue(contextWithBaggage, "romeo");
    try (Scope s = onlyScopeDecorator.decorateScope(contextWithBaggage, mock(Scope.class))) {
      assertThat(map).containsOnly(entry(FIELD.name(), "romeo"));
    }

    assertThat(map).isEqualTo(snapshot);
    map.clear();
  }

  @Test public void revertsChanges_withMultipleBaggageFields() {
    map.put("X-B3-TraceId", "000000000000000a");
    map.put(FIELD.name(), "bob");
    map.put(FIELD_2.name(), "BV");
    map.put(LOCAL_FIELD.name(), "ef01");
    Map<String, String> snapshot = new LinkedHashMap<>(map);

    FIELD.baggageField().updateValue(contextWithBaggage, "romeo");
    FIELD_2.baggageField().updateValue(contextWithBaggage, "FO");
    LOCAL_FIELD.baggageField().updateValue(contextWithBaggage, "abcd");

    try (Scope s = withBaggageFieldsDecorator.decorateScope(contextWithBaggage, mock(Scope.class))) {
      assertThat(map).containsOnly(
        entry("X-B3-TraceId", "0000000000000001"),
        entry(FIELD.name(), "romeo"),
        entry(FIELD_2.name(), "FO"),
        entry(LOCAL_FIELD.name(), "abcd")
      );
    }
    assertThat(map).isEqualTo(snapshot);
    map.clear();
  }

  @Test public void revertsChanges_withMultipleBaggageFields_FlushOnUpdate() {
    map.put("traceId", "000000000000000a");
    map.put("spanId", "000000000000000b");
    map.put(FIELD.name(), "bob");
    map.put(FIELD_2.name(), "BV");
    map.put(LOCAL_FIELD.name(), "ef01");
    map.put("flushed", "word");
    Map<String, String> snapshot = new LinkedHashMap<>(map);

    FIELD.baggageField().updateValue(contextWithBaggage, "romeo");
    FIELD_2.baggageField().updateValue(contextWithBaggage, "FO");
    LOCAL_FIELD.baggageField().updateValue(contextWithBaggage, "abcd");
    FLUSH_FIELD.baggageField().updateValue(contextWithBaggage, "excel");
    try (
      Scope s = withFlushOnUpdateScopeDecorator.decorateScope(contextWithBaggage, mock(Scope.class))) {
      assertThat(map).containsOnly(
        entry("traceId", "0000000000000001"),
        entry("spanId", "0000000000000003"),
        entry(FIELD.name(), "romeo"),
        entry(FIELD_2.name(), "FO"),
        entry(LOCAL_FIELD.name(), "abcd"),
        entry("flushed", "excel")
      );
    }

    assertThat(map).isEqualTo(snapshot);
    map.clear();
  }

  @Test public void revertsLateChanges() {
    try (Scope s = decorator.decorateScope(contextWithBaggage, mock(Scope.class))) {
      // late changes
      map.put("traceId", "000000000000000a");
      map.put("spanId", "000000000000000c");
    }
  }

  @Test public void revertsLateChanges_onlyTraceId() {
    try (Scope s = onlyTraceIdDecorator.decorateScope(contextWithBaggage, mock(Scope.class))) {
      // late changes
      map.put("X-B3-TraceId", "000000000000000a");
    }
  }

  @Test public void revertsLateChanges_onlyBaggageField() {
    FIELD.baggageField().updateValue(contextWithBaggage, "romeo");
    try (Scope s = onlyScopeDecorator.decorateScope(contextWithBaggage, mock(Scope.class))) {
      // late changes
      map.put(FIELD.name(), "bob");
    }
    assertThat(map).isEmpty();

    FLUSH_FIELD.baggageField().updateValue(contextWithBaggage, "excel");
    try (
      Scope s = onlyFlushOnUpdateScopeDecorator.decorateScope(contextWithBaggage, mock(Scope.class))) {
      // late changes
      map.put("flushed", "word");
    }
    assertThat(map).isEmpty();
  }

  @Test public void revertsLateChanges_withMultipleBaggageFields() {
    FIELD.baggageField().updateValue(contextWithBaggage, "romeo");
    FIELD_2.baggageField().updateValue(contextWithBaggage, "FO");
    LOCAL_FIELD.baggageField().updateValue(contextWithBaggage, "abcd");

    try (Scope s = withBaggageFieldsDecorator.decorateScope(contextWithBaggage, mock(Scope.class))) {
      // late changes
      map.put("X-B3-TraceId", "000000000000000a");
      map.put(FIELD.name(), "bob");
    }
    assertThat(map).isEmpty();

    FLUSH_FIELD.baggageField().updateValue(contextWithBaggage, "excel");
    try (
      Scope s = withFlushOnUpdateScopeDecorator.decorateScope(contextWithBaggage, mock(Scope.class))) {
      // late changes
      map.put("flushed", "word");
    }
    assertThat(map).isEmpty();
  }

  @Test public void ignoresUpdate_onlyBaggageField() {
    FIELD.baggageField().updateValue(contextWithBaggage, "romeo");
    try (Scope s = onlyScopeDecorator.decorateScope(contextWithBaggage, mock(Scope.class))) {
      FIELD.baggageField().updateValue(contextWithBaggage, "bob");
      assertThat(map).containsEntry(FIELD.name(), "romeo");
    }
    assertThat(map).isEmpty();
  }

  @Test public void flushOnUpdate_onlyBaggageField() {
    FLUSH_FIELD.baggageField().updateValue(contextWithBaggage, "excel");
    assertNestedUpdatesCoherent(withFlushOnUpdateScopeDecorator);
    assertThat(map).isEmpty();
  }

  @Test public void ignoresUpdate_withMultipleBaggageFields() {
    FIELD.baggageField().updateValue(contextWithBaggage, "romeo");
    FIELD_2.baggageField().updateValue(contextWithBaggage, "FO");
    LOCAL_FIELD.baggageField().updateValue(contextWithBaggage, "abcd");

    try (Scope s = withBaggageFieldsDecorator.decorateScope(contextWithBaggage, mock(Scope.class))) {
      Map<String, String> snapshot = new LinkedHashMap<>(map);
      FIELD.baggageField().updateValue(contextWithBaggage, "bob");
      assertThat(map).isEqualTo(snapshot);
    }
    assertThat(map).isEmpty();
  }

  @Test public void flushOnUpdate_multipleBaggageFields() {
    FIELD.baggageField().updateValue(contextWithBaggage, "romeo");
    FIELD_2.baggageField().updateValue(contextWithBaggage, "FO");
    LOCAL_FIELD.baggageField().updateValue(contextWithBaggage, "abcd");
    FLUSH_FIELD.baggageField().updateValue(contextWithBaggage, "excel");

    assertNestedUpdatesCoherent(withFlushOnUpdateScopeDecorator);
    assertThat(map).isEmpty();
  }

  void assertNestedUpdatesCoherent(ScopeDecorator decorator) {
    try (Scope s = decorator.decorateScope(contextWithBaggage, mock(Scope.class))) {
      FLUSH_FIELD.baggageField().updateValue(contextWithBaggage, "word");
      try (Scope s1 = decorator.decorateScope(contextWithBaggage, mock(Scope.class))) {
        assertThat(map).containsEntry("flushed", "word");
        FLUSH_FIELD.baggageField().updateValue(contextWithBaggage, "outlook");
        try (Scope s2 = decorator.decorateScope(contextWithBaggage, mock(Scope.class))) {
          assertThat(map).containsEntry("flushed", "outlook");
          FLUSH_FIELD.baggageField().updateValue(contextWithBaggage, "powerpoint");
          try (Scope s3 = decorator.decorateScope(contextWithBaggage, mock(Scope.class))) {
            assertThat(map).containsEntry("flushed", "powerpoint");
            FLUSH_FIELD.baggageField().updateValue(contextWithBaggage, "sharepoint");
            assertThat(map).containsEntry("flushed", "sharepoint");
          }
          assertThat(map).containsEntry("flushed", "powerpoint");
        }
        assertThat(map).containsEntry("flushed", "outlook");
      }
      assertThat(map).containsEntry("flushed", "word");
    }
  }

  static final class TestBuilder extends CorrelationScopeDecorator.Builder {
    TestBuilder() {
      super(MapContext.INSTANCE);
    }
  }

  enum MapContext implements CorrelationContext {
    INSTANCE;

    @Override public String getValue(String name) {
      return map.get(name);
    }

    @Override public boolean update(String name, @Nullable String value) {
      if (value != null) {
        map.put(name, value);
      } else if (map.containsKey(name)) {
        map.remove(name);
      } else {
        return false;
      }
      return true;
    }
  }
}

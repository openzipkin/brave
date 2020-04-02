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
import org.junit.Before;
import org.junit.Test;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;

public class CorrelationScopeDecoratorTest {
  static final BaggageField BAGGAGE_FIELD = BaggageField.create("user-id");
  static final BaggageField BAGGAGE_FIELD_2 = BaggageField.create("country-code");
  static final BaggageField LOCAL_BAGGAGE_FIELD = BaggageField.create("serviceId");
  static final BaggageField FLUSHABLE_BAGGAGE_FIELD = BaggageField.newBuilder("bp")
    .flushOnUpdate().build();
  static final Map<String, String> map = new LinkedHashMap<>();

  Propagation.Factory baggageFactory = BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY)
    .addField(LOCAL_BAGGAGE_FIELD)
    .addRemoteField(BAGGAGE_FIELD)
    .addRemoteField(BAGGAGE_FIELD_2)
    .addRemoteField(FLUSHABLE_BAGGAGE_FIELD)
    .build();

  TraceContext context = baggageFactory.decorate(TraceContext.newBuilder()
    .traceId(1L)
    .parentId(2L)
    .spanId(3L)
    .sampled(true)
    .build());

  ScopeDecorator decorator = new TestBuilder().build();
  ScopeDecorator onlyTraceIdDecorator = new TestBuilder()
    .clear()
    .addField(BaggageFields.TRACE_ID, "X-B3-TraceId")
    .build();
  ScopeDecorator onlyBaggageFieldDecorator = new TestBuilder()
    .clear()
    .addField(BAGGAGE_FIELD)
    .build();
  ScopeDecorator withBaggageFieldsDecorator = new TestBuilder()
    .clear()
    .addField(BaggageFields.TRACE_ID, "X-B3-TraceId")
    .addField(BAGGAGE_FIELD)
    .addField(LOCAL_BAGGAGE_FIELD)
    .addField(BAGGAGE_FIELD_2)
    .build();
  ScopeDecorator withFlushableBaggageFieldDecorator = new TestBuilder()
    .addField(BAGGAGE_FIELD)
    .addField(LOCAL_BAGGAGE_FIELD)
    .addField(BAGGAGE_FIELD_2)
    .addField(FLUSHABLE_BAGGAGE_FIELD, "flushed")
    .build();
  ScopeDecorator onlyFlushableBaggageFieldDecorator = new TestBuilder()
    .clear()
    .addField(FLUSHABLE_BAGGAGE_FIELD, "flushed")
    .build();
  ScopeDecorator withDirtyFieldDecorator = new TestBuilder()
    .addField(BAGGAGE_FIELD, "dirty")
    .addDirtyName("dirty")
    .build();
  ScopeDecorator onlyDirtyFieldDecorator = new TestBuilder()
    .clear()
    .addField(BAGGAGE_FIELD, "dirty")
    .addDirtyName("dirty")
    .build();

  @Before public void before() {
    map.clear();
  }

  @Test public void no_dupes() {
    CorrelationScopeDecorator.Builder builder = new TestBuilder().addField(FLUSHABLE_BAGGAGE_FIELD);

    assertThatThrownBy(() -> builder.addField(FLUSHABLE_BAGGAGE_FIELD))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Field already added: bp");
  }

  @Test public void dirtyMustBeAName() {
    CorrelationScopeDecorator.Builder builder = new TestBuilder();

    assertThatThrownBy(() -> builder.addDirtyName("dirty"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Correlation name not in use: dirty");
  }

  @Test public void name_clear_and_add() {
    CorrelationScopeDecorator.Builder builder = new TestBuilder()
      .addField(BAGGAGE_FIELD, "dirty")
      .addDirtyName("dirty");

    Map<BaggageField, String> fieldToNames = builder.fieldToNames();
    Set<String> dirtyNames = builder.dirtyNames();

    builder.clear();

    fieldToNames.forEach(builder::addField);
    dirtyNames.forEach(builder::addDirtyName);

    assertThat(builder)
      .usingRecursiveComparison()
      .isEqualTo(new TestBuilder()
        .addField(BAGGAGE_FIELD, "dirty")
        .addDirtyName("dirty"));
  }

  @Test public void doesntDecorateNoop() {
    assertThat(decorator.decorateScope(context, Scope.NOOP)).isSameAs(Scope.NOOP);
    assertThat(decorator.decorateScope(null, Scope.NOOP)).isSameAs(Scope.NOOP);
  }

  @Test public void dirtyFieldsMatchScope() {
    CorrelationUpdateScope.Single scopeOne =
      (CorrelationUpdateScope.Single) onlyDirtyFieldDecorator.decorateScope(context, Scope.NOOP);
    assertThat(scopeOne.dirty).isTrue();
    scopeOne.close();

    CorrelationUpdateScope.Multiple scopeMultiple =
      (CorrelationUpdateScope.Multiple) withDirtyFieldDecorator.decorateScope(context, Scope.NOOP);

    BitSet bitset = BitSet.valueOf(new long[] {scopeMultiple.dirty});
    assertThat(scopeMultiple.names)
      .containsExactly("traceId", "spanId", "dirty");

    assertThat(bitset.get(0)).isFalse();
    assertThat(bitset.get(1)).isFalse();
    assertThat(bitset.get(2)).isTrue();
  }

  @Test public void decoratesNoop_matchingDirtyField() {
    BAGGAGE_FIELD.updateValue(context, "romeo");
    map.put("dirty", "romeo");

    decoratesNoop_dirtyField();
  }

  @Test public void decoratesNoop_matchingNullDirtyField() {
    decoratesNoop_dirtyField();
  }

  /**
   * All dirty fields should be reverted at the end of the scope, because end-users can interfere
   * with the underlying context in the middle of the scope. (ex. MDC.put)
   */
  void decoratesNoop_dirtyField() {
    assertThat(withDirtyFieldDecorator.decorateScope(context, Scope.NOOP))
      .isNotSameAs(Scope.NOOP);
    assertThat(onlyDirtyFieldDecorator.decorateScope(context, Scope.NOOP))
      .isNotSameAs(Scope.NOOP);
  }

  /** Fields that don't flush inside a s have no value and no value of the underlying context. */
  @Test public void doesntDecorateNoop_matchingNullBaggageField() {
    assertThat(onlyTraceIdDecorator.decorateScope(context, Scope.NOOP)).isSameAs(Scope.NOOP);
    assertThat(withBaggageFieldsDecorator.decorateScope(context, Scope.NOOP)).isSameAs(Scope.NOOP);
    assertThat(onlyBaggageFieldDecorator.decorateScope(context, Scope.NOOP)).isSameAs(Scope.NOOP);
  }

  /** Even when values match, flushable fields can update later, so NOOP can't be used. */
  @Test public void decoratesNoop_matchingNullFlushableBaggageField() {
    assertThat(onlyFlushableBaggageFieldDecorator.decorateScope(context, Scope.NOOP))
      .isNotSameAs(Scope.NOOP);
    assertThat(withFlushableBaggageFieldDecorator.decorateScope(context, Scope.NOOP))
      .isNotSameAs(Scope.NOOP);
  }

  /** Fields that don't flush inside a s match the values of the underlying context. */
  @Test public void doesntDecorateNoop_matchingBaggageField() {
    BAGGAGE_FIELD.updateValue(context, "romeo");
    BAGGAGE_FIELD_2.updateValue(context, "FO");
    LOCAL_BAGGAGE_FIELD.updateValue(context, "abcd");
    map.put(BAGGAGE_FIELD.name(), "romeo");
    map.put(BAGGAGE_FIELD_2.name(), "FO");
    map.put(LOCAL_BAGGAGE_FIELD.name(), "abcd");

    assertThat(onlyTraceIdDecorator.decorateScope(context, Scope.NOOP)).isSameAs(Scope.NOOP);
    assertThat(withBaggageFieldsDecorator.decorateScope(context, Scope.NOOP)).isSameAs(Scope.NOOP);
    assertThat(onlyBaggageFieldDecorator.decorateScope(context, Scope.NOOP)).isSameAs(Scope.NOOP);
  }

  /** When a context is in an unexpected state, save off fields and revert. */
  @Test public void decoratesNoop_unconfiguredFields() {
    context = context.toBuilder().extra(Collections.emptyList()).build();

    for (ScopeDecorator decorator : asList(withBaggageFieldsDecorator, onlyBaggageFieldDecorator)) {
      map.put(BAGGAGE_FIELD.name(), "romeo");
      map.put(BAGGAGE_FIELD_2.name(), "FO");
      map.put(LOCAL_BAGGAGE_FIELD.name(), "abcd");

      assertThat(decorator.decorateScope(context, Scope.NOOP)).isNotSameAs(Scope.NOOP);
    }
  }

  @Test public void doesntRevertMultipleTimes_singleField() {
    BAGGAGE_FIELD.updateValue(context, "romeo");
    map.put(BAGGAGE_FIELD.name(), "romeo");

    try (Scope s = onlyBaggageFieldDecorator.decorateScope(null, Scope.NOOP)) {
      assertThat(map).isEmpty();
      s.close();
      assertThat(map).isNotEmpty();
      map.clear();

      s.close();
      assertThat(map).isEmpty(); // didn't revert again
    }

    map.put("flushed", "excel");
    FLUSHABLE_BAGGAGE_FIELD.updateValue(context, "excel");

    try (Scope s = onlyFlushableBaggageFieldDecorator.decorateScope(null, mock(Scope.class))) {
      assertThat(map).isEmpty();
      s.close();
      assertThat(map).isNotEmpty();
      map.clear();

      s.close();
      assertThat(map).isEmpty(); // didn't revert again
    }
  }

  @Test public void doesntRevertMultipleTimes_multipleFields() {
    BAGGAGE_FIELD.updateValue(context, "romeo");
    BAGGAGE_FIELD_2.updateValue(context, "FO");
    LOCAL_BAGGAGE_FIELD.updateValue(context, "abcd");
    map.put(BAGGAGE_FIELD_2.name(), "FO");
    map.put(LOCAL_BAGGAGE_FIELD.name(), "abcd");
    map.put(BAGGAGE_FIELD.name(), "romeo");

    try (Scope s = withBaggageFieldsDecorator.decorateScope(null, Scope.NOOP)) {
      assertThat(map).isEmpty();
      s.close();
      assertThat(map).isNotEmpty();
      map.clear();

      s.close();
      assertThat(map).isEmpty(); // didn't revert again
    }

    map.put("flushed", "excel");
    FLUSHABLE_BAGGAGE_FIELD.updateValue(context, "excel");

    try (Scope s = withFlushableBaggageFieldDecorator.decorateScope(null, mock(Scope.class))) {
      assertThat(map).isEmpty();
      s.close();
      assertThat(map).isNotEmpty();
      map.clear();

      s.close();
      assertThat(map).isEmpty(); // didn't revert again
    }
  }

  @Test public void decoratesNoop_nullMeansclear() {
    context = context.toBuilder().extra(Collections.emptyList()).build();

    BAGGAGE_FIELD.updateValue(context, "romeo");
    BAGGAGE_FIELD_2.updateValue(context, "FO");
    LOCAL_BAGGAGE_FIELD.updateValue(context, "abcd");
    FLUSHABLE_BAGGAGE_FIELD.updateValue(context, "excel");
    map.put(BAGGAGE_FIELD.name(), "romeo");
    map.put(BAGGAGE_FIELD_2.name(), "FO");
    map.put(LOCAL_BAGGAGE_FIELD.name(), "abcd");

    try (Scope s = withBaggageFieldsDecorator.decorateScope(null, Scope.NOOP)) {
      assertThat(map).isEmpty();
    }

    try (Scope s = onlyBaggageFieldDecorator.decorateScope(null, Scope.NOOP)) {
      assertThat(map).doesNotContainKey(BAGGAGE_FIELD.name());
    }

    FLUSHABLE_BAGGAGE_FIELD.updateValue(context, "excel");
    map.put("flushed", "excel");

    try (Scope s = withFlushableBaggageFieldDecorator.decorateScope(null, Scope.NOOP)) {
      assertThat(map).isEmpty();
    }

    try (Scope s = onlyFlushableBaggageFieldDecorator.decorateScope(null, Scope.NOOP)) {
      assertThat(map).doesNotContainKey("flushed");
    }
  }

  @Test public void addsAndRemoves() {
    try (Scope s = decorator.decorateScope(context, mock(Scope.class))) {
      assertThat(map).containsOnly(
        entry("traceId", "0000000000000001"),
        entry("spanId", "0000000000000003")
      );
    }
    assertThat(map.isEmpty());
  }

  @Test public void addsAndRemoves_onlyTraceId() {
    try (Scope s = onlyTraceIdDecorator.decorateScope(context, mock(Scope.class))) {
      assertThat(map).containsOnly(entry("X-B3-TraceId", "0000000000000001"));
    }
    assertThat(map.isEmpty());
  }

  @Test public void addsAndRemoves_onlyBaggageField() {
    BAGGAGE_FIELD.updateValue(context, "romeo");
    try (Scope s = onlyBaggageFieldDecorator.decorateScope(context, mock(Scope.class))) {
      assertThat(map).containsOnly(entry(BAGGAGE_FIELD.name(), "romeo"));
    }
    assertThat(map.isEmpty());

    FLUSHABLE_BAGGAGE_FIELD.updateValue(context, "excel");
    try (Scope s = onlyFlushableBaggageFieldDecorator.decorateScope(context, mock(Scope.class))) {
      assertThat(map).containsOnly(entry("flushed", "excel"));
    }

    assertThat(map.isEmpty());
  }

  @Test public void addsAndRemoves_withMultipleBaggageField() {
    BAGGAGE_FIELD.updateValue(context, "romeo");
    BAGGAGE_FIELD_2.updateValue(context, "FO");
    LOCAL_BAGGAGE_FIELD.updateValue(context, "abcd");

    try (Scope s = withBaggageFieldsDecorator.decorateScope(context, mock(Scope.class))) {
      assertThat(map).containsOnly(
        entry("X-B3-TraceId", "0000000000000001"),
        entry(BAGGAGE_FIELD.name(), "romeo"),
        entry(BAGGAGE_FIELD_2.name(), "FO"),
        entry(LOCAL_BAGGAGE_FIELD.name(), "abcd")
      );
    }
    assertThat(map.isEmpty());

    FLUSHABLE_BAGGAGE_FIELD.updateValue(context, "excel");
    try (Scope s = withFlushableBaggageFieldDecorator.decorateScope(context, mock(Scope.class))) {
      assertThat(map).containsOnly(
        entry("traceId", "0000000000000001"),
        entry("spanId", "0000000000000003"),
        entry(BAGGAGE_FIELD.name(), "romeo"),
        entry(BAGGAGE_FIELD_2.name(), "FO"),
        entry(LOCAL_BAGGAGE_FIELD.name(), "abcd"),
        entry("flushed", "excel")
      );
    }
    assertThat(map.isEmpty());
  }

  @Test public void revertsChanges() {
    map.put("traceId", "000000000000000a");
    map.put("spanId", "000000000000000c");
    Map<String, String> snapshot = new LinkedHashMap<>(map);

    try (Scope s = decorator.decorateScope(context, mock(Scope.class))) {
      assertThat(map).containsOnly(
        entry("traceId", "0000000000000001"),
        entry("spanId", "0000000000000003")
      );
    }

    assertThat(map).isEqualTo(snapshot);
  }

  @Test public void revertsChanges_onlyTraceId() {
    map.put("X-B3-TraceId", "000000000000000a");
    Map<String, String> snapshot = new LinkedHashMap<>(map);

    try (Scope s = onlyTraceIdDecorator.decorateScope(context, mock(Scope.class))) {
      assertThat(map).containsOnly(entry("X-B3-TraceId", "0000000000000001"));
    }

    assertThat(map).isEqualTo(snapshot);
  }

  @Test public void revertsChanges_onlyBaggageField() {
    map.put(BAGGAGE_FIELD.name(), "bob");
    Map<String, String> snapshot = new LinkedHashMap<>(map);

    BAGGAGE_FIELD.updateValue(context, "romeo");
    try (Scope s = onlyBaggageFieldDecorator.decorateScope(context, mock(Scope.class))) {
      assertThat(map).containsOnly(entry(BAGGAGE_FIELD.name(), "romeo"));
    }

    assertThat(map).isEqualTo(snapshot);
  }

  @Test public void revertsChanges_withMultipleBaggageFields() {
    map.put("X-B3-TraceId", "000000000000000a");
    map.put(BAGGAGE_FIELD.name(), "bob");
    map.put(BAGGAGE_FIELD_2.name(), "BV");
    map.put(LOCAL_BAGGAGE_FIELD.name(), "ef01");
    Map<String, String> snapshot = new LinkedHashMap<>(map);

    BAGGAGE_FIELD.updateValue(context, "romeo");
    BAGGAGE_FIELD_2.updateValue(context, "FO");
    LOCAL_BAGGAGE_FIELD.updateValue(context, "abcd");

    try (Scope s = withBaggageFieldsDecorator.decorateScope(context, mock(Scope.class))) {
      assertThat(map).containsOnly(
        entry("X-B3-TraceId", "0000000000000001"),
        entry(BAGGAGE_FIELD.name(), "romeo"),
        entry(BAGGAGE_FIELD_2.name(), "FO"),
        entry(LOCAL_BAGGAGE_FIELD.name(), "abcd")
      );
    }
    assertThat(map).isEqualTo(snapshot);
  }

  @Test public void revertsChanges_withMultipleBaggageFields_flushable() {
    map.put("traceId", "000000000000000a");
    map.put("spanId", "000000000000000b");
    map.put(BAGGAGE_FIELD.name(), "bob");
    map.put(BAGGAGE_FIELD_2.name(), "BV");
    map.put(LOCAL_BAGGAGE_FIELD.name(), "ef01");
    map.put("flushed", "word");
    Map<String, String> snapshot = new LinkedHashMap<>(map);

    BAGGAGE_FIELD.updateValue(context, "romeo");
    BAGGAGE_FIELD_2.updateValue(context, "FO");
    LOCAL_BAGGAGE_FIELD.updateValue(context, "abcd");
    FLUSHABLE_BAGGAGE_FIELD.updateValue(context, "excel");
    try (Scope s = withFlushableBaggageFieldDecorator.decorateScope(context, mock(Scope.class))) {
      assertThat(map).containsOnly(
        entry("traceId", "0000000000000001"),
        entry("spanId", "0000000000000003"),
        entry(BAGGAGE_FIELD.name(), "romeo"),
        entry(BAGGAGE_FIELD_2.name(), "FO"),
        entry(LOCAL_BAGGAGE_FIELD.name(), "abcd"),
        entry("flushed", "excel")
      );
    }

    assertThat(map).isEqualTo(snapshot);
  }

  @Test public void revertsLateChanges() {
    try (Scope s = decorator.decorateScope(context, mock(Scope.class))) {
      // late changes
      map.put("traceId", "000000000000000a");
      map.put("spanId", "000000000000000c");
    }
    assertThat(map).isEmpty();
  }

  @Test public void revertsLateChanges_onlyTraceId() {
    try (Scope s = onlyTraceIdDecorator.decorateScope(context, mock(Scope.class))) {
      // late changes
      map.put("X-B3-TraceId", "000000000000000a");
    }
    assertThat(map).isEmpty();
  }

  @Test public void revertsLateChanges_onlyBaggageField() {
    BAGGAGE_FIELD.updateValue(context, "romeo");
    try (Scope s = onlyBaggageFieldDecorator.decorateScope(context, mock(Scope.class))) {
      // late changes
      map.put(BAGGAGE_FIELD.name(), "bob");
    }
    assertThat(map).isEmpty();

    FLUSHABLE_BAGGAGE_FIELD.updateValue(context, "excel");
    try (Scope s = onlyFlushableBaggageFieldDecorator.decorateScope(context, mock(Scope.class))) {
      // late changes
      map.put("flushed", "word");
    }
    assertThat(map).isEmpty();
  }

  @Test public void revertsLateChanges_withMultipleBaggageFields() {
    BAGGAGE_FIELD.updateValue(context, "romeo");
    BAGGAGE_FIELD_2.updateValue(context, "FO");
    LOCAL_BAGGAGE_FIELD.updateValue(context, "abcd");

    try (Scope s = withBaggageFieldsDecorator.decorateScope(context, mock(Scope.class))) {
      // late changes
      map.put("X-B3-TraceId", "000000000000000a");
      map.put(BAGGAGE_FIELD.name(), "bob");
    }
    assertThat(map).isEmpty();

    FLUSHABLE_BAGGAGE_FIELD.updateValue(context, "excel");
    try (Scope s = withFlushableBaggageFieldDecorator.decorateScope(context, mock(Scope.class))) {
      // late changes
      map.put("flushed", "word");
    }
    assertThat(map).isEmpty();
  }

  @Test public void ignoresUpdate_onlyBaggageField() {
    BAGGAGE_FIELD.updateValue(context, "romeo");
    try (Scope s = onlyBaggageFieldDecorator.decorateScope(context, mock(Scope.class))) {
      BAGGAGE_FIELD.updateValue(context, "bob");
      assertThat(map).containsEntry(BAGGAGE_FIELD.name(), "romeo");
    }
    assertThat(map).isEmpty();
  }

  @Test public void flushOnUpdate_onlyBaggageField() {
    FLUSHABLE_BAGGAGE_FIELD.updateValue(context, "excel");
    assertNestedUpdatesCoherent(withFlushableBaggageFieldDecorator);
    assertThat(map).isEmpty();
  }

  @Test public void ignoresUpdate_withMultipleBaggageFields() {
    BAGGAGE_FIELD.updateValue(context, "romeo");
    BAGGAGE_FIELD_2.updateValue(context, "FO");
    LOCAL_BAGGAGE_FIELD.updateValue(context, "abcd");

    try (Scope s = withBaggageFieldsDecorator.decorateScope(context, mock(Scope.class))) {
      Map<String, String> snapshot = new LinkedHashMap<>(map);
      BAGGAGE_FIELD.updateValue(context, "bob");
      assertThat(map).isEqualTo(snapshot);
    }
    assertThat(map).isEmpty();
  }

  @Test public void flushOnUpdate_multipleBaggageFields() {
    BAGGAGE_FIELD.updateValue(context, "romeo");
    BAGGAGE_FIELD_2.updateValue(context, "FO");
    LOCAL_BAGGAGE_FIELD.updateValue(context, "abcd");
    FLUSHABLE_BAGGAGE_FIELD.updateValue(context, "excel");

    assertNestedUpdatesCoherent(withFlushableBaggageFieldDecorator);
    assertThat(map).isEmpty();
  }

  void assertNestedUpdatesCoherent(ScopeDecorator decorator) {
    try (Scope s = decorator.decorateScope(context, mock(Scope.class))) {
      FLUSHABLE_BAGGAGE_FIELD.updateValue(context, "word");
      try (Scope s1 = decorator.decorateScope(context, mock(Scope.class))) {
        assertThat(map).containsEntry("flushed", "word");
        FLUSHABLE_BAGGAGE_FIELD.updateValue(context, "outlook");
        try (Scope s2 = decorator.decorateScope(context, mock(Scope.class))) {
          assertThat(map).containsEntry("flushed", "outlook");
          FLUSHABLE_BAGGAGE_FIELD.updateValue(context, "powerpoint");
          try (Scope s3 = decorator.decorateScope(context, mock(Scope.class))) {
            assertThat(map).containsEntry("flushed", "powerpoint");
            FLUSHABLE_BAGGAGE_FIELD.updateValue(context, "sharepoint");
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

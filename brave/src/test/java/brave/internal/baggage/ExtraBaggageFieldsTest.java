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

import brave.baggage.BaggageField;
import brave.propagation.TraceContext;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class ExtraBaggageFieldsTest {
  protected final BaggageField field1 = BaggageField.create("one");
  protected final BaggageField field2 = BaggageField.create("two");
  protected final BaggageField field3 = BaggageField.create("three");

  protected ExtraBaggageFields.Factory factory;
  protected ExtraBaggageFields extra;
  protected TraceContext context;

  /** Configure {@link #field1} and {@link #field2}, but not {@link #field3} */
  protected abstract ExtraBaggageFields.Factory newFactory();

  @Before public void setup() {
    factory = newFactory();
    extra = factory.create();
    context = TraceContext.newBuilder().traceId(1L).spanId(2L)
        .extra(Collections.singletonList(extra)).build();
  }

  @Test public void putValue() {
    field1.updateValue(context, "1");
    assertThat(field1.getValue(context)).isEqualTo("1");
    assertThat(isEmpty(extra)).isFalse();
  }

  @Test public void putValue_multiple() {
    field1.updateValue(context, "1");
    field2.updateValue(context, "2");
    assertThat(field1.getValue(context)).isEqualTo("1");
    assertThat(field2.getValue(context)).isEqualTo("2");

    field1.updateValue(context, null);
    assertThat(field1.getValue(context)).isNull();
    assertThat(field2.getValue(context)).isEqualTo("2");
    assertThat(isEmpty(extra)).isFalse();

    field2.updateValue(context, null);
    assertThat(field1.getValue(context)).isNull();
    assertThat(field2.getValue(context)).isNull();
    assertThat(isEmpty(extra)).isTrue();
  }

  @Test public void putValue_null_clearsState() {
    field1.updateValue(context, "1");
    field1.updateValue(context, null);
    assertThat(isEmpty(extra)).isTrue();
  }

  @Test public void putValueNoop() {
    field1.updateValue(context, null);
    assertThat(isEmpty(extra)).isTrue();

    field1.updateValue(context, "1");
    Object before = getState(extra, field1);
    field1.updateValue(context, "1");
    assertThat(getState(extra, field1)).isSameAs(before);
  }

  @Test public void getValue_ignored_if_unconfigured() {
    assertThat(field3.getValue(context)).isNull();
  }

  @Test public void getValue_null_if_not_set() {
    assertThat(field1.getValue(context)).isNull();
  }

  @Test public void getValue_ignore_if_not_defined() {
    assertThat(BaggageField.create("foo").getValue(context))
        .isNull();
  }

  /**
   * Ensures only field and value comparison are used in equals and hashCode. This makes sure we can
   * know if an extraction with baggage is empty or not.
   */
  @Test public void equalsAndHashCode() {
    // empty extraction is equivalent
    assertThat(factory.create())
        .isEqualTo(factory.create());
    assertThat(factory.create())
        .hasSameHashCodeAs(factory.create());

    field1.updateValue(context, "1");
    field2.updateValue(context, "2");

    ExtraBaggageFields extra2 = factory.create();
    TraceContext context2 = TraceContext.newBuilder().traceId(2L).spanId(2L)
        .extra(Collections.singletonList(extra2)).build();
    field1.updateValue(context2, "1");
    field2.updateValue(context2, "2");

    // same baggageState are equivalent
    assertThat(extra).isEqualTo(extra2);
    assertThat(extra).hasSameHashCodeAs(extra2);

    // different values are not equivalent
    field2.updateValue(context2, "3");
    assertThat(extra).isNotEqualTo(extra2);
    assertThat(extra.hashCode()).isNotEqualTo(extra2.hashCode());
  }

  Object getState(ExtraBaggageFields extraBaggageFields, BaggageField field) {
    int index = extraBaggageFields.indexOf(field);
    if (index == -1) return null;
    Object[] stateArray = extraBaggageFields.stateArray;
    if (stateArray == null) return null;
    return stateArray[index];
  }

  protected boolean isEmpty(ExtraBaggageFields extraBaggageFields) {
    Object[] stateArray = extraBaggageFields.stateArray;
    if (stateArray == null) return true;
    for (Object state : stateArray) {
      if (!isEmpty(state)) return false;
    }
    return true;
  }

  protected boolean isEmpty(Object state) {
    return state == null;
  }
}

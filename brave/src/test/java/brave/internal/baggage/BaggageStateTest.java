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
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class BaggageStateTest<S> {
  protected BaggageState.Factory factory;
  protected BaggageField field1 = BaggageField.create("one");
  protected BaggageField field2 = BaggageField.create("two");
  protected BaggageField field3 = BaggageField.create("three");

  /** Configure {@link #field1} and {@link #field2}, but not {@link #field3} */
  protected abstract BaggageState.Factory newFactory();

  @Before public void setup() {
    factory = newFactory();
  }

  @Test public void putValue() {
    BaggageState baggageState = factory.create();

    baggageState.updateValue(field1, "1");
    assertThat(baggageState.getValue(field1)).isEqualTo("1");
    assertThat(isEmpty(baggageState)).isFalse();
  }

  @Test public void putValue_multiple() {
    BaggageState baggageState = factory.create();

    baggageState.updateValue(field1, "1");
    baggageState.updateValue(field2, "2");
    assertThat(baggageState.getValue(field1)).isEqualTo("1");
    assertThat(baggageState.getValue(field2)).isEqualTo("2");

    baggageState.updateValue(field1, null);
    assertThat(baggageState.getValue(field1)).isNull();
    assertThat(baggageState.getValue(field2)).isEqualTo("2");
    assertThat(isEmpty(baggageState)).isFalse();

    baggageState.updateValue(field2, null);
    assertThat(baggageState.getValue(field1)).isNull();
    assertThat(baggageState.getValue(field2)).isNull();
    assertThat(isEmpty(baggageState)).isTrue();
  }

  @Test public void putValue_null_clearsState() {
    BaggageState baggageState = factory.create();

    baggageState.updateValue(field1, "1");
    baggageState.updateValue(field1, null);
    assertThat(isEmpty(baggageState)).isTrue();
  }

  @Test public void putValueNoop() {
    BaggageState baggageState = factory.create();

    baggageState.updateValue(field1, null);
    assertThat(isEmpty(baggageState)).isTrue();

    baggageState.updateValue(field1, "1");
    Object before = getState(baggageState, field1);
    baggageState.updateValue(field1, "1");
    assertThat(getState(baggageState, field1)).isSameAs(before);
  }

  @Test public void getValue_ignored_if_unconfigured() {
    BaggageState baggageState = factory.create();

    assertThat(baggageState.getValue(field3)).isNull();
  }

  @Test public void getValue_null_if_not_set() {
    BaggageState baggageState = factory.create();

    assertThat(baggageState.getValue(field1)).isNull();
  }

  @Test public void getValue_ignore_if_not_defined() {
    BaggageState baggageState = factory.create();

    assertThat(baggageState.getValue(BaggageField.create("foo")))
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

    BaggageState baggageState1 = factory.create();
    baggageState1.updateValue(field1, "1");
    baggageState1.updateValue(field2, "2");

    BaggageState baggageState2 = factory.create();
    baggageState2.updateValue(field1, "1");
    baggageState2.updateValue(field2, "2");

    // same baggageState are equivalent
    assertThat(baggageState1).isEqualTo(baggageState2);
    assertThat(baggageState1).hasSameHashCodeAs(baggageState2);

    // different values are not equivalent
    baggageState2.updateValue(field2, "3");
    assertThat(baggageState1).isNotEqualTo(baggageState2);
    assertThat(baggageState1.hashCode()).isNotEqualTo(baggageState2.hashCode());
  }

  Object getState(BaggageState baggageState, BaggageField field) {
    int index = ((BaggageState) baggageState).indexOf(field);
    if (index == -1) return null;
    Object[] stateArray = ((BaggageState) baggageState).stateArray;
    if (stateArray == null) return null;
    return stateArray[index];
  }

  protected boolean isEmpty(BaggageState baggageState) {
    Object[] stateArray = ((BaggageState) baggageState).stateArray;
    if (stateArray == null) return true;
    for (Object state : stateArray) {
      if (state != null) return false;
    }
    return true;
  }
}

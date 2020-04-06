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

public abstract class ExtraBaggageFieldsTest<S> {
  protected ExtraBaggageFields.Factory factory;
  protected BaggageField field1 = BaggageField.create("one");
  protected BaggageField field2 = BaggageField.create("two");
  protected BaggageField field3 = BaggageField.create("three");

  /** Configure {@link #field1} and {@link #field2}, but not {@link #field3} */
  protected abstract ExtraBaggageFields.Factory newFactory();

  @Before public void setup() {
    factory = newFactory();
  }

  @Test public void putValue() {
    ExtraBaggageFields extraBaggageFields = factory.create();

    extraBaggageFields.updateValue(field1, "1");
    assertThat(extraBaggageFields.getValue(field1)).isEqualTo("1");
    assertThat(isEmpty(extraBaggageFields)).isFalse();
  }

  @Test public void putValue_multiple() {
    ExtraBaggageFields extraBaggageFields = factory.create();

    extraBaggageFields.updateValue(field1, "1");
    extraBaggageFields.updateValue(field2, "2");
    assertThat(extraBaggageFields.getValue(field1)).isEqualTo("1");
    assertThat(extraBaggageFields.getValue(field2)).isEqualTo("2");

    extraBaggageFields.updateValue(field1, null);
    assertThat(extraBaggageFields.getValue(field1)).isNull();
    assertThat(extraBaggageFields.getValue(field2)).isEqualTo("2");
    assertThat(isEmpty(extraBaggageFields)).isFalse();

    extraBaggageFields.updateValue(field2, null);
    assertThat(extraBaggageFields.getValue(field1)).isNull();
    assertThat(extraBaggageFields.getValue(field2)).isNull();
    assertThat(isEmpty(extraBaggageFields)).isTrue();
  }

  @Test public void putValue_null_clearsState() {
    ExtraBaggageFields extraBaggageFields = factory.create();

    extraBaggageFields.updateValue(field1, "1");
    extraBaggageFields.updateValue(field1, null);
    assertThat(isEmpty(extraBaggageFields)).isTrue();
  }

  @Test public void putValueNoop() {
    ExtraBaggageFields extraBaggageFields = factory.create();

    extraBaggageFields.updateValue(field1, null);
    assertThat(isEmpty(extraBaggageFields)).isTrue();

    extraBaggageFields.updateValue(field1, "1");
    Object before = getState(extraBaggageFields, field1);
    extraBaggageFields.updateValue(field1, "1");
    assertThat(getState(extraBaggageFields, field1)).isSameAs(before);
  }

  @Test public void getValue_ignored_if_unconfigured() {
    ExtraBaggageFields extraBaggageFields = factory.create();

    assertThat(extraBaggageFields.getValue(field3)).isNull();
  }

  @Test public void getValue_null_if_not_set() {
    ExtraBaggageFields extraBaggageFields = factory.create();

    assertThat(extraBaggageFields.getValue(field1)).isNull();
  }

  @Test public void getValue_ignore_if_not_defined() {
    ExtraBaggageFields extraBaggageFields = factory.create();

    assertThat(extraBaggageFields.getValue(BaggageField.create("foo")))
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

    ExtraBaggageFields extraBaggageFields1 = factory.create();
    extraBaggageFields1.updateValue(field1, "1");
    extraBaggageFields1.updateValue(field2, "2");

    ExtraBaggageFields extraBaggageFields2 = factory.create();
    extraBaggageFields2.updateValue(field1, "1");
    extraBaggageFields2.updateValue(field2, "2");

    // same baggageState are equivalent
    assertThat(extraBaggageFields1).isEqualTo(extraBaggageFields2);
    assertThat(extraBaggageFields1).hasSameHashCodeAs(extraBaggageFields2);

    // different values are not equivalent
    extraBaggageFields2.updateValue(field2, "3");
    assertThat(extraBaggageFields1).isNotEqualTo(extraBaggageFields2);
    assertThat(extraBaggageFields1.hashCode()).isNotEqualTo(extraBaggageFields2.hashCode());
  }

  Object getState(ExtraBaggageFields extraBaggageFields, BaggageField field) {
    int index = ((ExtraBaggageFields) extraBaggageFields).indexOf(field);
    if (index == -1) return null;
    Object[] stateArray = ((ExtraBaggageFields) extraBaggageFields).stateArray;
    if (stateArray == null) return null;
    return stateArray[index];
  }

  protected boolean isEmpty(ExtraBaggageFields extraBaggageFields) {
    Object[] stateArray = ((ExtraBaggageFields) extraBaggageFields).stateArray;
    if (stateArray == null) return true;
    for (Object state : stateArray) {
      if (state != null) return false;
    }
    return true;
  }
}

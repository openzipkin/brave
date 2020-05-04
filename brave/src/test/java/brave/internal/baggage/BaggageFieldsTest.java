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

public abstract class BaggageFieldsTest {
  final BaggageField field1 = BaggageField.create("one");
  final BaggageField field2 = BaggageField.create("two");
  final BaggageField field3 = BaggageField.create("three");

  BaggageFieldsHandler handler;
  BaggageFields extra, extra2;

  /** Configure {@link #field1} and {@link #field2}, but not {@link #field3} */
  abstract BaggageFieldsHandler newHandler();

  @Before public void setup() {
    handler = newHandler();
    extra = handler.newExtra(null);
    extra2 = handler.newExtra(null);
  }

  @Test public void updateValue() {
    extra.updateValue(field1, "1");
    assertThat(extra.getValue(field1)).isEqualTo("1");
    assertThat(isStateEmpty(extra.state)).isFalse();
  }

  @Test public void updateValue_multiple() {
    extra.updateValue(field1, "1");
    extra.updateValue(field2, "2");
    assertThat(extra.getValue(field1)).isEqualTo("1");
    assertThat(extra.getValue(field2)).isEqualTo("2");

    extra.updateValue(field1, null);
    assertThat(extra.getValue(field1)).isNull();
    assertThat(extra.getValue(field2)).isEqualTo("2");
    assertThat(isStateEmpty(extra.state)).isFalse();

    extra.updateValue(field2, null);
    assertThat(extra.getValue(field1)).isNull();
    assertThat(extra.getValue(field2)).isNull();
    assertThat(isStateEmpty(extra.state)).isTrue();
  }

  @Test public void updateValue_null_clearsState() {
    extra.updateValue(field1, "1");
    extra.updateValue(field1, null);
    assertThat(isStateEmpty(extra.state)).isTrue();
  }

  @Test public void updateValueNoop() {
    extra.updateValue(field1, null);
    assertThat(isStateEmpty(extra.state)).isTrue();

    extra.updateValue(field1, "1");
    Object before = extra.state;
    extra.updateValue(field1, "1");
    assertThat(extra.state).isSameAs(before);
  }

  @Test public void getValue_ignored_if_unconfigured() {
    assertThat(extra.getValue(field3)).isNull();
  }

  @Test public void getValue_null_if_not_set() {
    assertThat(extra.getValue(field1)).isNull();
  }

  @Test public void getValue_ignore_if_not_defined() {
    assertThat(extra.getValue(BaggageField.create("foo")))
        .isNull();
  }

  @Test public void mergeStateKeepingOursOnConflict_bothEmpty() {
    Object before = extra.state;
    extra.mergeStateKeepingOursOnConflict(extra2);
    assertThat(before).isSameAs(extra.state);

    assertThat(isStateEmpty(extra.state)).isTrue();
  }

  @Test public void mergeStateKeepingOursOnConflict_empty_nonEmpty() {
    extra2.updateValue(field1, "1");
    extra2.updateValue(field2, "2");

    Object before = extra.state;
    extra.mergeStateKeepingOursOnConflict(extra2);
    assertThat(before).isNotSameAs(extra.state);

    assertThat(extra.getValue(field1)).isEqualTo("1");
    assertThat(extra.getValue(field2)).isEqualTo("2");
  }

  @Test public void mergeStateKeepingOursOnConflict_nonEmpty_empty() {
    extra.updateValue(field1, "1");
    extra.updateValue(field2, "2");

    Object before = extra.state;
    extra.mergeStateKeepingOursOnConflict(extra2);
    assertThat(before).isSameAs(extra.state);

    assertThat(extra.getValue(field1)).isEqualTo("1");
    assertThat(extra.getValue(field2)).isEqualTo("2");
  }

  @Test public void mergeStateKeepingOursOnConflict_noConflict() {
    extra.updateValue(field1, "1");
    extra.updateValue(field2, "2");
    extra2.updateValue(field2, "2");

    Object before = extra.state;
    extra.mergeStateKeepingOursOnConflict(extra2);
    assertThat(before).isSameAs(extra.state);

    assertThat(extra.getValue(field1)).isEqualTo("1");
    assertThat(extra.getValue(field2)).isEqualTo("2");
  }

  @Test public void mergeStateKeepingOursOnConflict_oursWinsOnConflict() {
    extra.updateValue(field1, "1");
    extra.updateValue(field2, "2");
    extra2.updateValue(field2, "1");

    Object before = extra.state;
    extra.mergeStateKeepingOursOnConflict(extra2);
    assertThat(before).isSameAs(extra.state);

    assertThat(extra.getValue(field1)).isEqualTo("1");
    assertThat(extra.getValue(field2)).isEqualTo("2");
  }

  /**
   * Ensures only field and value comparison are used in equals and hashCode. This makes sure we can
   * know if an extraction with baggage is empty or not.
   */
  @Test public void equalsAndHashCode() {
    // empty extraction is equivalent
    assertThat(handler.newExtra(null))
        .isEqualTo(handler.newExtra(null));
    assertThat(handler.newExtra(null))
        .hasSameHashCodeAs(handler.newExtra(null));

    extra.updateValue(field1, "1");
    extra.updateValue(field2, "2");

    BaggageFields extra2 = handler.newExtra(null);
    extra2.updateValue(field1, "1");
    extra2.updateValue(field2, "2");

    // same baggageState are equivalent
    assertThat(extra).isEqualTo(extra2);
    assertThat(extra).hasSameHashCodeAs(extra2);

    // different values are not equivalent
    extra2.updateValue(field2, "3");
    assertThat(extra).isNotEqualTo(extra2);
    assertThat(extra.hashCode()).isNotEqualTo(extra2.hashCode());
  }

  final boolean isStateEmpty(Object state) {
    Object[] array = (Object[]) state;
    for (int i = 0; i < array.length; i += 2) {
      if (array[i + 1] != null) return false;
    }
    return true;
  }
}

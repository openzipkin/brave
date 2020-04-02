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

import brave.baggage.PredefinedBaggageFields.Factory;
import brave.internal.PropagationFields;
import brave.internal.PropagationFieldsFactoryTest;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PredefinedBaggageFieldsTest
  extends PropagationFieldsFactoryTest<BaggageField, String, PredefinedBaggageFields, Factory> {

  public PredefinedBaggageFieldsTest() {
    super(BaggageField.create("one"), BaggageField.create("two"), "1", "2", "3");
  }

  @Override protected Factory newFactory() {
    return new Factory(keyOne, keyTwo);
  }

  @Override protected String name(BaggageField field) {
    return field.name();
  }

  @Test public void put_ignore_if_not_defined() {
    PropagationFields.put(context, BaggageField.create("balloon-color"), "red", factory.type());

    assertThat(((PropagationFields) context.extra().get(0)).toMap())
      .isEmpty();
  }

  @Test public void put_ignore_if_not_defined_index() {
    PredefinedBaggageFields fields = factory.create();

    fields.put(4, "red");

    assertThat(fields)
      .isEqualToComparingFieldByField(factory.create());
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

    PredefinedBaggageFields fields1 = factory.create();
    fields1.put(keyOne, valueOne);
    fields1.put(keyTwo, valueTwo);

    PredefinedBaggageFields fields2 = factory.create();
    fields2.put(keyOne, valueOne);
    fields2.put(keyTwo, valueTwo);

    // same fields are equivalent
    assertThat(fields1).isEqualTo(fields2);
    assertThat(fields1).hasSameHashCodeAs(fields2);

    // different values are not equivalent
    fields2.put(keyTwo, valueThree);
    assertThat(fields1).isNotEqualTo(fields2);
    assertThat(fields1.hashCode()).isNotEqualTo(fields2.hashCode());
  }

  @Test public void put_idempotent() {
    PredefinedBaggageFields fields = factory.create();

    fields.put(keyOne, "red");
    String[] fieldsArray = fields.values;

    fields.put(keyOne, "red");
    assertThat(fields.values)
      .isSameAs(fieldsArray);

    fields.put(keyOne, "blue");
    assertThat(fields.values)
      .isNotSameAs(fieldsArray);
  }

  @Test public void get_ignore_if_not_defined_index() {
    PredefinedBaggageFields fields = factory.create();

    assertThat(fields.get(4))
      .isNull();
  }

  @Test public void toMap_one_index() {
    PredefinedBaggageFields fields = factory.create();
    fields.put(1, "a");

    assertThat(fields.toMap())
      .hasSize(1)
      .containsEntry(keyTwo.lcName, "a");
  }

  @Test public void toMap_two_index() {
    PredefinedBaggageFields fields = factory.create();
    fields.put(0, "1");
    fields.put(1, "a");

    assertThat(fields.toMap())
      .hasSize(2)
      .containsEntry(keyOne.lcName, "1")
      .containsEntry(keyTwo.lcName, "a");
  }
}

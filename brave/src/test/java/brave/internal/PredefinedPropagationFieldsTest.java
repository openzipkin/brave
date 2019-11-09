/*
 * Copyright 2013-2019 The OpenZipkin Authors
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

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PredefinedPropagationFieldsTest
  extends PropagationFieldsFactoryTest<String, String, PredefinedPropagationFields> {
  public PredefinedPropagationFieldsTest() {
    super("one", "two", "1", "2", "3");
  }

  @Override
  protected PropagationFieldsFactory<String, String, PredefinedPropagationFields> newFactory() {
    return new PropagationFieldsFactory<String, String, PredefinedPropagationFields>() {
      @Override public Class<PredefinedPropagationFields> type() {
        return PredefinedPropagationFields.class;
      }

      @Override public PredefinedPropagationFields create() {
        return new PredefinedPropagationFields(keyOne, keyTwo);
      }

      @Override protected PredefinedPropagationFields create(PredefinedPropagationFields parent) {
        return new PredefinedPropagationFields(parent, keyOne, keyTwo);
      }
    };
  }

  @Test public void put_ignore_if_not_defined() {
    PropagationFields.put(context, "balloon-color", "red", factory.type());

    assertThat(((PropagationFields) context.extra().get(0)).toMap())
      .isEmpty();
  }

  @Test public void put_ignore_if_not_defined_index() {
    PredefinedPropagationFields fields = factory.create();

    fields.put(4, "red");

    assertThat(fields)
      .isEqualToComparingFieldByField(factory.create());
  }

  @Test public void put_idempotent() {
    PredefinedPropagationFields fields = factory.create();

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
    PredefinedPropagationFields fields = factory.create();

    assertThat(fields.get(4))
      .isNull();
  }

  @Test public void toMap_one_index() {
    PredefinedPropagationFields fields = factory.create();
    fields.put(1, "a");

    assertThat(fields.toMap())
      .hasSize(1)
      .containsEntry(keyTwo, "a");
  }

  @Test public void toMap_two_index() {
    PredefinedPropagationFields fields = factory.create();
    fields.put(0, "1");
    fields.put(1, "a");

    assertThat(fields.toMap())
      .hasSize(2)
      .containsEntry(keyOne, "1")
      .containsEntry(keyTwo, "a");
  }
}

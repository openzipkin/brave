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
package brave.features.baggage;

import brave.baggage.BaggageField;
import brave.internal.baggage.BaggageHandler;
import brave.internal.baggage.BaggageHandlers;
import brave.internal.baggage.ExtraBaggageFields;
import brave.internal.baggage.ExtraBaggageFieldsTest;
import java.util.Map;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** This is an internal feature until we settle on an encoding format. */
public class DynamicBaggageTest extends ExtraBaggageFieldsTest {
  BaggageHandler<String> singleValueHandler = BaggageHandlers.string(field1);
  BaggageHandler<Map<BaggageField, String>> dynamicHandler = DynamicBaggageHandler.create();

  @Override protected boolean isEmpty(Object state) {
    return state == null || (state instanceof Map && ((Map) state).isEmpty());
  }

  // Here, we add one constant field and one handler for everything else
  @Override protected ExtraBaggageFields.Factory newFactory() {
    return ExtraBaggageFields.newFactory(singleValueHandler, dynamicHandler);
  }

  @Test public void fieldsAreNotConstant() {
    ExtraBaggageFields extraBaggageFields = factory.create();

    assertThat(extraBaggageFields.getAllFields()).containsOnly(field1);

    assertThat(extraBaggageFields.getAllFields()).containsOnly(field1);

    extraBaggageFields.updateValue(field2, "2");
    extraBaggageFields.updateValue(field3, "3");
    assertThat(extraBaggageFields.getAllFields()).containsOnly(field1, field2, field3);

    extraBaggageFields.updateValue(field1, null);
    // Field one is not dynamic so it stays in the list
    assertThat(extraBaggageFields.getAllFields()).containsOnly(field1, field2, field3);

    extraBaggageFields.updateValue(field2, null);
    // dynamic fields are also not pruned from the list
    assertThat(extraBaggageFields.getAllFields()).containsOnly(field1, field2, field3);
  }

  @Test public void encodes_arbitrary_fields() {
    ExtraBaggageFields extraBaggageFields = factory.create();

    extraBaggageFields.updateValue(field1, "1");
    extraBaggageFields.updateValue(field2, "2");
    extraBaggageFields.updateValue(field3, "3");

    assertThat(extraBaggageFields.getRequestValue(singleValueHandler))
      .isEqualTo("1");
    assertThat(extraBaggageFields.getRequestValue(dynamicHandler))
      .contains(""
        + "two=2\n"
        + "three=3");
  }
}

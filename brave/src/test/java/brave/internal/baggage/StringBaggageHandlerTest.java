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
import java.util.List;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StringBaggageHandlerTest extends ExtraBaggageFieldsTest {
  BaggageHandler<String> handler1 = BaggageHandlers.string(field1);
  BaggageHandler<String> handler2 = BaggageHandlers.string(field2);

  @Override protected ExtraBaggageFields.Factory newFactory() {
    return new ExtraBaggageFieldsFactory(handler1, handler2);
  }

  @Test public void fieldsAreConstant() {
    List<BaggageField> withNoValues = extra.getAllFields();
    field1.updateValue(context, "1");
    field2.updateValue(context, "3");

    assertThat(extra.getAllFields())
      .isSameAs(withNoValues);
  }

  @Test public void putValue_ignores_if_not_defined() {
    field3.updateValue(context, "1");

    assertThat(isEmpty(extra)).isTrue();
  }

  @Test public void getState() {
    field1.updateValue(context, "1");

    assertThat(extra.getState(handler1)).isEqualTo("1");
  }

  @Test public void getState_null_if_not_set() {
    assertThat(extra.getState(handler1)).isNull();
  }

  @Test public void getState_ignored_if_unconfigured() {
    assertThat(extra.getState(BaggageHandlers.string(field3))).isNull();
  }
}

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

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class FixedBaggageFieldsTest extends BaggageFieldsTest {
  @Override BaggageFieldsHandler newHandler() {
    return BaggageFieldsHandler.create(asList(field1, field2), false);
  }

  @Test public void getAllFields() {
    List<BaggageField> withNoValues = extra.getAllFields();
    extra.updateValue(field1, "1");
    extra.updateValue(field2, "3");

    assertThat(extra.getAllFields())
        .isSameAs(withNoValues);
  }

  @Test public void putValue_ignores_if_not_defined() {
    extra.updateValue(field3, "1");

    assertThat(isStateEmpty(extra.array())).isTrue();
  }
}

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

import org.junit.Test;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class DynamicBaggageFieldsTest extends BaggageFieldsTest {
  @Override BaggageFieldsFactory newFactory() {
    return BaggageFieldsFactory.create(asList(field1), true);
  }

  @Test public void getAllFields_areNotConstant() {
    // returns fields passed in newFactory
    assertThat(extra.getAllFields()).containsOnly(field1);

    extra.updateValue(field2, "2");
    extra.updateValue(field3, "3");
    assertThat(extra.getAllFields()).containsOnly(field1, field2, field3);

    extra.updateValue(field1, null);
    // Field one is not dynamic so it stays in the list
    assertThat(extra.getAllFields()).containsOnly(field1, field2, field3);

    extra.updateValue(field2, null);
    // dynamic fields are also not pruned from the list
    assertThat(extra.getAllFields()).containsOnly(field1, field2, field3);
  }
}

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

import brave.internal.baggage.BaggageState;
import brave.internal.baggage.BaggageStateTest;
import java.util.Map;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** This is an internal feature until we settle on an encoding format. */
public class DynamicBaggageTest extends BaggageStateTest<Map<String, String>> {
  DynamicBaggageStateHandler handler = new DynamicBaggageStateHandler();

  @Override protected BaggageState.Factory newFactory() {
    return BaggageState.newFactory(handler);
  }

  @Test public void encodes_arbitrary_fields() {
    BaggageState baggageState = factory.create();

    baggageState.updateValue(field1, "1");
    baggageState.updateValue(field2, "2");
    baggageState.updateValue(field3, "3");

    assertThat(baggageState.encodeState(handler))
      .contains(""
        + "one=1\n"
        + "two=2\n"
        + "three=3");
  }
}

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
import brave.baggage.BaggagePropagationConfig.SingleBaggageField;
import brave.internal.InternalBaggage;
import brave.internal.baggage.BaggageHandler;
import brave.internal.baggage.BaggageHandlers;
import brave.internal.baggage.ExtraBaggageFields;
import brave.internal.baggage.ExtraBaggageFieldsTest;
import brave.propagation.B3Propagation;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Injector;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

import static brave.baggage.BaggagePropagation.newFactoryBuilder;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/** This is an internal feature until we settle on an encoding format. */
public class DynamicBaggageTest extends ExtraBaggageFieldsTest {
  BaggageHandler<String> stringHandler = BaggageHandlers.string(field1);
  DynamicBaggageHandler dynamicBaggageHandler = DynamicBaggageHandler.get();

  @Override protected boolean isEmpty(Object state) {
    return state == null || (state instanceof Map && ((Map) state).isEmpty());
  }

  // Here, we add one constant field and one handler for everything else
  @Override protected ExtraBaggageFields.Factory newFactory() {
    return ExtraBaggageFields.newFactory(asList(stringHandler, dynamicBaggageHandler));
  }

  @Test public void fieldsAreNotConstant() {
    assertThat(BaggageField.getAll(context)).containsOnly(field1);

    assertThat(BaggageField.getAll(context)).containsOnly(field1);

    field2.updateValue(context, "2");
    field3.updateValue(context, "3");
    assertThat(BaggageField.getAll(context)).containsOnly(field1, field2, field3);

    field1.updateValue(context, null);
    // Field one is not dynamic so it stays in the list
    assertThat(BaggageField.getAll(context)).containsOnly(field1, field2, field3);

    field2.updateValue(context, null);
    // dynamic fields are also not pruned from the list
    assertThat(BaggageField.getAll(context)).containsOnly(field1, field2, field3);
  }

  @Test public void getState() {
    field1.updateValue(context, "1");
    field2.updateValue(context, "2");
    field3.updateValue(context, "3");

    assertThat(extra.getState(stringHandler))
        .isEqualTo("1");

    assertThat(extra.getState(dynamicBaggageHandler))
        .containsEntry(field2, "2")
        .containsEntry(field3, "3");
  }

  /** This shows that we can encode arbitrary fieds into a single header. */
  @Test public void encodes_arbitrary_fields() {
    Propagation.Factory factory = newFactoryBuilder(B3Propagation.newFactoryBuilder()
        .injectFormat(B3Propagation.Format.SINGLE).build())
        .add(SingleBaggageField.remote(field1))
        .add(InternalBaggage.instance.newBaggagePropagationConfig(
            "baggage",
            dynamicBaggageHandler,
            dynamicBaggageHandler,
            dynamicBaggageHandler
        )).build();

    TraceContext context = factory.decorate(TraceContext.newBuilder().traceId(1).spanId(2).build());
    field1.updateValue(context, "1");
    field2.updateValue(context, "2");
    field3.updateValue(context, "3");

    Injector<Map<String, String>> injector = factory.get().injector(Map::put);
    Map<String, String> headers = new LinkedHashMap<>();
    injector.inject(context, headers);

    assertThat(headers).containsOnly(
        entry("b3", "0000000000000001-0000000000000002"),
        entry("one", "1"),
        entry("baggage", "two=2,three=3")
    );
  }
}

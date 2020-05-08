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

import brave.baggage.Access;
import brave.baggage.BaggageField;
import brave.baggage.BaggagePropagationConfig.SingleBaggageField;
import brave.propagation.B3Propagation;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Injector;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

import static brave.baggage.BaggagePropagation.newFactoryBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/** This is an internal feature until we settle on an encoding format. */
public class BaggageInSingleHeaderTest {
  BaggageField field1 = BaggageField.create("one");
  BaggageField field2 = BaggageField.create("two");
  BaggageField field3 = BaggageField.create("three");

  Propagation.Factory factory = newFactoryBuilder(B3Propagation.newFactoryBuilder()
      .injectFormat(B3Propagation.Format.SINGLE).build())
      .add(SingleBaggageField.remote(field1))
      .add(SingleBaggageField.local(field2))
      .add(Access.newBaggagePropagationConfig(SingleHeaderCodec.get(), 32))
      .build();

  /** This shows that we can encode arbitrary fields into a single header. */
  @Test public void encodes_arbitrary_fields() {
    TraceContext context = factory.decorate(TraceContext.newBuilder().traceId(1).spanId(2).build());
    field1.updateValue(context, "1");
    field2.updateValue(context, "2");
    field3.updateValue(context, "3");

    Injector<Map<String, String>> injector = factory.get().injector(Map::put);
    Map<String, String> headers = new LinkedHashMap<>();
    injector.inject(context, headers);

    assertThat(headers).containsOnly(
        entry("b3", "0000000000000001-0000000000000002"),
        entry("one", "1"), // has its own header config which is still serialized
        entry("baggage", "one=1,three=3") // excluding the blacklist field including the dynamic one
    );
  }
}

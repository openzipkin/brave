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
package brave.internal;

import brave.baggage.BaggagePropagationConfig;
import brave.internal.baggage.BaggageHandler;
import brave.internal.baggage.BaggageHandler.StateDecoder;
import brave.internal.baggage.BaggageHandler.StateEncoder;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class InternalBaggageTest {
  @Test public void newBaggagePropagationConfig() {
    BaggageHandler<String> baggageHandler = mock(BaggageHandler.class);
    StateDecoder<String> stateDecoder = mock(StateDecoder.class);
    StateEncoder<String> stateEncoder = mock(StateEncoder.class);

    BaggagePropagationConfig<String> config = InternalBaggage.instance.newBaggagePropagationConfig(
        "baggage",
        baggageHandler,
        stateDecoder,
        stateEncoder
    );

    assertThat(config.extractKeyNames())
        .containsExactly("baggage");
    assertThat(config.injectKeyNames())
        .containsExactly("baggage");
    assertThat(config).extracting("baggageHandler")
        .isSameAs(baggageHandler);
    assertThat(config).extracting("stateDecoder")
        .isSameAs(stateDecoder);
    assertThat(config).extracting("stateEncoder")
        .isSameAs(stateEncoder);
  }
}

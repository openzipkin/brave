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
package brave.messaging;

import brave.Tracing;
import brave.propagation.B3Propagation;
import brave.propagation.Propagation;
import org.junit.Test;

import static brave.sampler.SamplerFunctions.deferDecision;
import static brave.sampler.SamplerFunctions.neverSample;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class MessagingTracingTest {
  Tracing tracing = mock(Tracing.class);

  @Test public void defaultSamplersDefer() {
    MessagingTracing messagingTracing = MessagingTracing.newBuilder(tracing).build();

    assertThat(messagingTracing.producerSampler())
      .isSameAs(deferDecision());
    assertThat(messagingTracing.consumerSampler())
      .isSameAs(deferDecision());
  }

  @Test public void toBuilder() {
    MessagingTracing messagingTracing = MessagingTracing.newBuilder(tracing).build();

    assertThat(messagingTracing.toBuilder().build())
      .usingRecursiveComparison()
      .isEqualTo(messagingTracing);

    assertThat(messagingTracing.toBuilder().producerSampler(neverSample()).build())
      .usingRecursiveComparison()
      .isEqualTo(MessagingTracing.newBuilder(tracing).producerSampler(neverSample()).build());
  }

  @Test public void canOverridePropagation() {
    Propagation<String> propagation = B3Propagation.newFactoryBuilder()
      .injectFormat(B3Propagation.Format.SINGLE)
      .build().get();

    MessagingTracing messagingTracing = MessagingTracing.newBuilder(tracing)
      .propagation(propagation)
      .build();

    assertThat(messagingTracing.propagation())
      .isSameAs(propagation);

    messagingTracing = MessagingTracing.create(tracing).toBuilder()
      .propagation(propagation)
      .build();

    assertThat(messagingTracing.propagation())
      .isSameAs(propagation);
  }
}

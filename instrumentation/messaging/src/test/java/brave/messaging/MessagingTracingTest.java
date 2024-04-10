/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.messaging;

import brave.Tracing;
import brave.propagation.B3Propagation;
import brave.propagation.Propagation;
import org.junit.jupiter.api.Test;

import static brave.sampler.SamplerFunctions.deferDecision;
import static brave.sampler.SamplerFunctions.neverSample;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class MessagingTracingTest {
  Tracing tracing = mock(Tracing.class);

  @Test void defaultSamplersDefer() {
    MessagingTracing messagingTracing = MessagingTracing.newBuilder(tracing).build();

    assertThat(messagingTracing.producerSampler())
      .isSameAs(deferDecision());
    assertThat(messagingTracing.consumerSampler())
      .isSameAs(deferDecision());
  }

  @Test void toBuilder() {
    MessagingTracing messagingTracing = MessagingTracing.newBuilder(tracing).build();

    assertThat(messagingTracing.toBuilder().build())
      .usingRecursiveComparison()
      .isEqualTo(messagingTracing);

    assertThat(messagingTracing.toBuilder().producerSampler(neverSample()).build())
      .usingRecursiveComparison()
      .isEqualTo(MessagingTracing.newBuilder(tracing).producerSampler(neverSample()).build());
  }

  @Test void canOverridePropagation() {
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

/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static brave.messaging.MessagingRequestMatchers.channelKindEquals;
import static brave.messaging.MessagingRequestMatchers.channelNameEquals;
import static brave.messaging.MessagingRequestMatchers.operationEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MessagingRequestMatchersTest {
  @Mock MessagingRequest request;

  @Test void operationEquals_matched() {
    when(request.operation()).thenReturn("send");

    assertThat(operationEquals("send").matches(request)).isTrue();
  }

  @Test void operationEquals_unmatched_mixedCase() {
    when(request.operation()).thenReturn("send");

    assertThat(operationEquals("Send").matches(request)).isFalse();
  }

  @Test void operationEquals_unmatched() {
    when(request.operation()).thenReturn("receive");

    assertThat(operationEquals("send").matches(request)).isFalse();
  }

  @Test void operationEquals_unmatched_null() {
    assertThat(operationEquals("send").matches(request)).isFalse();
  }

  @Test void channelKindEquals_matched() {
    when(request.channelKind()).thenReturn("queue");

    assertThat(channelKindEquals("queue").matches(request)).isTrue();
  }

  @Test void channelKindEquals_unmatched_mixedCase() {
    when(request.channelKind()).thenReturn("queue");

    assertThat(channelKindEquals("Queue").matches(request)).isFalse();
  }

  @Test void channelKindEquals_unmatched() {
    when(request.channelKind()).thenReturn("topic");

    assertThat(channelKindEquals("queue").matches(request)).isFalse();
  }

  @Test void channelKindEquals_unmatched_null() {
    assertThat(channelKindEquals("queue").matches(request)).isFalse();
  }

  @Test void channelNameEquals_matched() {
    when(request.channelName()).thenReturn("alerts");

    assertThat(channelNameEquals("alerts").matches(request)).isTrue();
  }

  @Test void channelNameEquals_unmatched_mixedCase() {
    when(request.channelName()).thenReturn("alerts");

    assertThat(channelNameEquals("Alerts").matches(request)).isFalse();
  }

  @Test void channelNameEquals_unmatched() {
    when(request.channelName()).thenReturn("complaints");

    assertThat(channelNameEquals("alerts").matches(request)).isFalse();
  }

  @Test void channelNameEquals_unmatched_null() {
    assertThat(channelNameEquals("alerts").matches(request)).isFalse();
  }
}

/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jakarta.jms;

import jakarta.jms.TextMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.core.client.impl.ClientMessageImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQTextMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MessagePropertiesTest {
  TextMessage message;

  @BeforeEach void setup() {
    final ClientSession clientSession = mock(ClientSession.class);
    when(clientSession.createMessage(anyByte(), eq(true), eq(0L), anyLong(), eq((byte) 4)))
      .thenReturn(new ClientMessageImpl());
    message = new ActiveMQTextMessage(clientSession);
  }

  @Test void getPropertyIfString() throws Exception {
    message.setStringProperty("b3", "1");

    assertThat(MessageProperties.getPropertyIfString(message, "b3"))
      .isEqualTo("1");
  }

  @Test void getPropertyIfString_notString() throws Exception {
    message.setByteProperty("b3", (byte) 0);

    assertThat(MessageProperties.getPropertyIfString(message, "b3"))
      .isNull();
  }

  @Test void getPropertyIfString_null() {
    assertThat(MessageProperties.getPropertyIfString(message, "b3")).isNull();
  }

  @Test void setStringProperty() throws Exception {
    MessageProperties.setStringProperty(message, "b3", "1");

    assertThat(message.getObjectProperty("b3"))
      .isEqualTo("1");
  }

  @Test void setStringProperty_replace() throws Exception {
    message.setByteProperty("b3", (byte) 0);
    MessageProperties.setStringProperty(message, "b3", "1");

    assertThat(message.getObjectProperty("b3"))
      .isEqualTo("1");
  }
}

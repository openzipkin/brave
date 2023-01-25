/*
 * Copyright 2013-2022 The OpenZipkin Authors
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
package brave.jakarta.jms;

import jakarta.jms.JMSException;
import jakarta.jms.TextMessage;

import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.core.client.impl.ClientMessageImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQTextMessage;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MessagePropertiesTest {
  TextMessage message;

  @Before public void setup() throws JMSException {
    final ClientSession clientSession = mock(ClientSession.class);
    when(clientSession.createMessage(anyByte(), eq(true), eq(0L), anyLong(), eq((byte)4)))
      .thenReturn(new ClientMessageImpl());
    message = new ActiveMQTextMessage(clientSession);
  }
  
  @Test public void getPropertyIfString() throws Exception {
    message.setStringProperty("b3", "1");

    assertThat(MessageProperties.getPropertyIfString(message, "b3"))
        .isEqualTo("1");
  }

  @Test public void getPropertyIfString_notString() throws Exception {
    message.setByteProperty("b3", (byte) 0);

    assertThat(MessageProperties.getPropertyIfString(message, "b3"))
        .isNull();
  }

  @Test public void getPropertyIfString_null() {
    assertThat(MessageProperties.getPropertyIfString(message, "b3")).isNull();
  }

  @Test public void setStringProperty() throws Exception {
    MessageProperties.setStringProperty(message, "b3", "1");

    assertThat(message.getObjectProperty("b3"))
        .isEqualTo("1");
  }

  @Test public void setStringProperty_replace() throws Exception {
    message.setByteProperty("b3", (byte) 0);
    MessageProperties.setStringProperty(message, "b3", "1");

    assertThat(message.getObjectProperty("b3"))
        .isEqualTo("1");
  }
}

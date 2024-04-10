/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jms;

import javax.jms.TextMessage;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MessagePropertiesTest {
  TextMessage message = new ActiveMQTextMessage();

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

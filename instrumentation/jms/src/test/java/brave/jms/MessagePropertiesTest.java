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
package brave.jms;

import javax.jms.TextMessage;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MessagePropertiesTest {
  TextMessage message = new ActiveMQTextMessage();

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

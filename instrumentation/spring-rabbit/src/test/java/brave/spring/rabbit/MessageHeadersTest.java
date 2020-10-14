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
package brave.spring.rabbit;

import org.junit.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

public class MessageHeadersTest {
  Message message = MessageBuilder.withBody(new byte[0]).build();

  @Test public void getHeaderIfString_noProperties() {
    message = new Message(new byte[0], null);

    assertThat(MessageHeaders.getHeaderIfString(message, "b3"))
      .isNull();
  }

  @Test public void getHeaderIfString() {
    message.getMessageProperties().setHeader("b3", "1");

    assertThat(MessageHeaders.getHeaderIfString(message, "b3"))
      .isEqualTo("1");
  }

  @Test public void getHeaderIfString_notString() {
    message.getMessageProperties().setHeader("b3", (byte) 0);

    assertThat(MessageHeaders.getHeaderIfString(message, "b3"))
      .isNull();
  }

  @Test public void getHeaderIfString_null() {
    assertThat(MessageHeaders.getHeaderIfString(message, "b3")).isNull();
  }

  @Test public void setHeader_noProperties() {
    message = new Message(new byte[0], null);

    MessageHeaders.setHeader(message, "b3", "1"); // doesn't crash
  }

  @Test public void setHeader() {
    MessageHeaders.setHeader(message, "b3", "1");

    assertThat((String) message.getMessageProperties().getHeader("b3"))
      .isEqualTo("1");
  }

  @Test public void setHeader_replace() {
    message.getMessageProperties().setHeader("b3", (byte) 0);
    MessageHeaders.setHeader(message, "b3", "1");

    assertThat((String) message.getMessageProperties().getHeader("b3"))
      .isEqualTo("1");
  }
}

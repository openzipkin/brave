/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.spring.rabbit;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;

public class MessageHeadersTest {
  Message message = MessageBuilder.withBody(new byte[0]).build();

  @Test void getHeaderIfString_noProperties() {
    message = new Message(new byte[0], null);

    assertThat(MessageHeaders.getHeaderIfString(message, "b3"))
      .isNull();
  }

  @Test void getHeaderIfString() {
    message.getMessageProperties().setHeader("b3", "1");

    assertThat(MessageHeaders.getHeaderIfString(message, "b3"))
      .isEqualTo("1");
  }

  @Test void getHeaderIfString_notString() {
    message.getMessageProperties().setHeader("b3", (byte) 0);

    assertThat(MessageHeaders.getHeaderIfString(message, "b3"))
      .isNull();
  }

  @Test void getHeaderIfString_null() {
    assertThat(MessageHeaders.getHeaderIfString(message, "b3")).isNull();
  }

  @Test void setHeader_noProperties() {
    message = new Message(new byte[0], null);

    MessageHeaders.setHeader(message, "b3", "1"); // doesn't crash
  }

  @Test void setHeader() {
    MessageHeaders.setHeader(message, "b3", "1");

    assertThat((String) message.getMessageProperties().getHeader("b3"))
      .isEqualTo("1");
  }

  @Test void setHeader_replace() {
    message.getMessageProperties().setHeader("b3", (byte) 0);
    MessageHeaders.setHeader(message, "b3", "1");

    assertThat((String) message.getMessageProperties().getHeader("b3"))
      .isEqualTo("1");
  }
}

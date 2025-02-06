/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.rocketmq.client;

import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MessagePropertiesTest {
  Message message = new Message();

  @Test void putUserProperty_noProperties() {
    message.putUserProperty("b3", "1");

    assertThat(message.getProperty("b3"))
        .isEqualTo("1");
  }
  
  @Test void putUserProperty_replace() {
    message.putUserProperty("b3", String.valueOf((byte) 0));
    message.putUserProperty("b3", "1");

    assertThat(message.getUserProperty("b3"))
        .isEqualTo("1");
  }

  @Test void getUserProperty_hasProperties() {
    message = new Message("topic", new byte[0]);
    message.putUserProperty("b3", "1");

    assertThat(message.getProperty("b3"))
        .isEqualTo("1");
  }
  
  @Test void getUserProperty_null() {
    assertThat(message.getProperty("b3")).isNull();
  }
}

/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.spring.rabbit;

import brave.internal.Nullable;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class MessageHeaders {
  /**
   * If {@link MessageProperties} exist, this returns {@link MessageProperties#getHeader(String)} if
   * it is a string.
   */
  @Nullable static String getHeaderIfString(Message message, String name) {
    MessageProperties properties = message.getMessageProperties();
    if (properties == null) return null;
    Object o = properties.getHeader(name);
    if (o instanceof String) return o.toString();
    return null;
  }

  /**
   * If {@link MessageProperties} exist, this invokes {@link MessageProperties#setHeader(String,
   * Object)}.
   */
  static void setHeader(Message message, String name, String value) {
    MessageProperties properties = message.getMessageProperties();
    if (properties == null) return;
    properties.setHeader(name, value);
  }
}

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

/*
 * Copyright 2013-2019 The OpenZipkin Authors
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

import brave.internal.Platform;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import javax.jms.JMSException;
import javax.jms.Message;

import static brave.jms.JmsTracing.log;

// Similar to https://github.com/apache/camel/blob/b9a3117f19dd19abd2ea8b789c42c3e86fe4c488/components/camel-jms/src/main/java/org/apache/camel/component/jms/JmsMessageHelper.java
final class PropertyFilter {
  /**
   * This implies copying properties because the JMS spec says you can't write properties upon
   * receipt until {@link Message#clearProperties()} has been called.
   *
   * <p> See https://docs.oracle.com/javaee/6/api/javax/jms/Message.html
   */
  static void filterProperties(Message message, Set<String> namesToClear) {
    ArrayList<Object> retainedProperties = messagePropertiesBuffer();
    try {
      filterProperties(message, namesToClear, retainedProperties);
    } finally {
      retainedProperties.clear(); // ensure no object references are held due to any exception
    }
  }

  static void filterProperties(Message message, Set<String> namesToClear, List<Object> out) {
    Enumeration<?> names;
    try {
      names = message.getPropertyNames();
    } catch (JMSException e) {
      Platform.get().log("error getting property names from {0}", message, e);
      return;
    }

    while (names.hasMoreElements()) {
      String name = (String) names.nextElement();
      Object value;
      try {
        value = message.getObjectProperty(name);
      } catch (JMSException e) {
        log(e, "error getting property {0} from message {1}", name, message);
        return;
      }
      if (!namesToClear.contains(name) && value != null) {
        out.add(name);
        out.add(value);
      }
    }

    // redo the properties to keep
    try {
      message.clearProperties();
    } catch (JMSException e) {
      Platform.get().log("error clearing properties of {0}", message, e);
      return;
    }

    for (int i = 0, length = out.size(); i < length; i += 2) {
      String name = out.get(i).toString();
      try {
        message.setObjectProperty(name, out.get(i + 1));
      } catch (JMSException e) {
        log(e, "error setting property {0} on message {1}", name, message);
        // continue on error when re-setting properties as it is better than not.
      }
    }
  }

  static final ThreadLocal<ArrayList<Object>> MESSAGE_PROPERTIES_BUFFER = new ThreadLocal<>();

  /** Also use pair indexing for temporary message properties: (name, value). */
  static ArrayList<Object> messagePropertiesBuffer() {
    ArrayList<Object> messagePropertiesBuffer = MESSAGE_PROPERTIES_BUFFER.get();
    if (messagePropertiesBuffer == null) {
      messagePropertiesBuffer = new ArrayList<>();
      MESSAGE_PROPERTIES_BUFFER.set(messagePropertiesBuffer);
    }
    return messagePropertiesBuffer;
  }
}

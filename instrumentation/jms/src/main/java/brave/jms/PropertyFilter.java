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
import javax.jms.JMSProducer;
import javax.jms.JMSRuntimeException;
import javax.jms.Message;

import static brave.jms.JmsTracing.log;

enum PropertyFilter {
  MESSAGE {
    /**
     * This implies copying properties because the JMS spec says you can't write properties until
     * {@link Message#clearProperties()} has been called.
     *
     * <p> See https://docs.oracle.com/javaee/6/api/javax/jms/Message.html
     */
    @Override void filterProperties(Object object, Set<String> namesToClear) {
      Message message = (Message) object;
      ArrayList<Object> retainedProperties = messagePropertiesBuffer();
      try {
        filterProperties(message, namesToClear, retainedProperties);
      } finally {
        retainedProperties.clear(); // ensure no object references are held due to JMS exceptions
      }
    }
  },
  JMS_PRODUCER {
    @Override void filterProperties(Object object, Set<String> namesToClear) {
      JMSProducer jmsProducer = (JMSProducer) object;
      ArrayList<Object> retainedProperties = messagePropertiesBuffer();
      try {
        JMS2.filterProperties(jmsProducer, namesToClear, retainedProperties);
      } finally {
        retainedProperties.clear(); // ensure no object references are held due to JMS exceptions
      }
    }
  };

  abstract void filterProperties(Object message, Set<String> namesToClear);

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

  static class JMS2 { // sneaky way to delay resolution of JMSRuntimeException
    static void filterProperties(JMSProducer producer, Set<String> namesToClear, List<Object> out) {
      Set<String> names;
      try {
        names = producer.getPropertyNames();
      } catch (JMSRuntimeException e) {
        Platform.get().log("error getting property names from {0}", producer, e);
        return;
      }

      boolean needsClear = false;
      for (String name : names) {
        Object value;
        try {
          value = producer.getObjectProperty(name);
        } catch (JMSRuntimeException e) {
          log(e, "error getting property {0} from producer {1}", name, producer);
          return;
        }
        if (!namesToClear.contains(name) && value != null) {
          out.add(name);
          out.add(value);
        } else {
          needsClear = true;
        }
      }

      // If the producer hasn't set a property we need to clear, we can just return. Unlike JMS 1.1,
      // there's no special casing for property override. It is only special when removing.
      if (!needsClear) return;

      // There's no api for remove property, so we have to replay the properties to keep instead.
      producer.clearProperties();
      for (int i = 0, length = out.size(); i < length; i += 2) {
        String name = out.get(i).toString();
        try {
          producer.setProperty(name, out.get(i + 1));
        } catch (JMSRuntimeException e) {
          log(e, "error setting property {0} on producer {1}", name, producer);
          // continue on error when re-setting properties as it is better than not.
        }
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

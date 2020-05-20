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

import brave.messaging.MessagingTracing;
import brave.propagation.Propagation.Setter;
import brave.test.ITRemote;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.jms.JMSException;
import javax.jms.Message;

import static brave.jms.MessageProperties.setStringProperty;

public abstract class ITJms extends ITRemote {
  static final Setter<Message, String> SETTER = new Setter<Message, String>() {
    @Override public void put(Message message, String name, String value) {
      setStringProperty(message, name, value);
    }

    @Override public String toString() {
      return "Message::setStringProperty";
    }
  };

  MessagingTracing messagingTracing = MessagingTracing.create(tracing);
  JmsTracing jmsTracing = JmsTracing.create(messagingTracing);

  static Map<String, String> propertiesToMap(Message headers) {
    try {
      Map<String, String> result = new LinkedHashMap<>();
      Enumeration<String> names = headers.getPropertyNames();
      while (names.hasMoreElements()) {
        String name = names.nextElement();
        result.put(name, headers.getStringProperty(name));
      }
      return result;
    } catch (JMSException e) {
      throw new AssertionError(e);
    }
  }

  interface JMSRunnable {
    void run() throws JMSException;
  }
}

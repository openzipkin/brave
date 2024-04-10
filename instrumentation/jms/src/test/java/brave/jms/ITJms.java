/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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

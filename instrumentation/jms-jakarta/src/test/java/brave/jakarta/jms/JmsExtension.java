/*
 * Copyright 2013-2023 The OpenZipkin Authors
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
package brave.jakarta.jms;

import jakarta.jms.BytesMessage;
import jakarta.jms.Connection;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Queue;
import jakarta.jms.QueueConnection;
import jakarta.jms.QueueSession;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import jakarta.jms.Topic;
import jakarta.jms.TopicConnection;
import jakarta.jms.TopicSession;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQMessage;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public abstract class JmsExtension implements BeforeEachCallback, AfterEachCallback {
  String testName;
  String destinationName, queueName, topicName;

  Connection connection;
  Session session;
  Destination destination;

  QueueConnection queueConnection;
  QueueSession queueSession;
  Queue queue;

  TopicConnection topicConnection;
  TopicSession topicSession;
  Topic topic;

  JmsExtension() {
  }

  TextMessage newMessage(String text) throws JMSException {
    return queueSession.createTextMessage(text);
  }

  BytesMessage newBytesMessage(String text) throws JMSException {
    BytesMessage message = queueSession.createBytesMessage();
    message.writeUTF(text);
    return message;
  }

  abstract void setReadOnlyProperties(Message message, boolean readOnlyProperties)
    throws JMSException;

  abstract Connection newConnection() throws JMSException, Exception;

  abstract QueueConnection newQueueConnection() throws JMSException, Exception;

  abstract TopicConnection newTopicConnection() throws JMSException, Exception;

  @Override public void beforeEach(ExtensionContext context) throws Exception {
    Optional<Method> testMethod = context.getTestMethod();
    if (testMethod.isPresent()) {
      this.testName = testMethod.get().getName();
    }

    connection = newConnection();
    connection.start();
    // Pass redundant info as we can't user default method in activeMQ
    session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

    destinationName = testName + "-d";
    destination = session.createQueue(destinationName);

    queueConnection = newQueueConnection();
    queueConnection.start();
    queueSession = queueConnection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);

    queueName = testName + "-q";
    queue = queueSession.createQueue(queueName);

    topicConnection = newTopicConnection();
    topicConnection.start();
    topicSession = topicConnection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);

    topicName = testName + "-t";
    topic = topicSession.createTopic(topicName);
  }

  @Override public void afterEach(ExtensionContext context) throws Exception {
    try {
      session.close();
      connection.close();
      topicSession.close();
      topicConnection.close();
      queueSession.close();
      queueConnection.close();
    } catch (JMSException e) {
      throw new AssertionError(e);
    }
  }

  static class ActiveMQ extends JmsExtension {
    @Override Connection newConnection() throws JMSException {
      return new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false")
        .createConnection();
    }

    @Override QueueConnection newQueueConnection() throws JMSException {
      return new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false")
        .createQueueConnection();
    }

    @Override TopicConnection newTopicConnection() throws JMSException {
      return new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false")
        .createTopicConnection();
    }

    @Override void setReadOnlyProperties(Message message, boolean readOnlyProperties) {
      try {
        Field propertiesReadOnly = ActiveMQMessage.class.getDeclaredField("propertiesReadOnly");
        propertiesReadOnly.setAccessible(true);
        propertiesReadOnly.set(message, readOnlyProperties);
      } catch (Exception e) {
        throw new AssertionError(e);
      }
    }
  }
}

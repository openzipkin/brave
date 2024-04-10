/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jms;

import java.lang.reflect.Method;
import java.util.Optional;
import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.TopicSession;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQMessage;
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
      if (session != null) session.close();
      if (connection != null) connection.close();
      if (topicSession != null) topicSession.close();
      if (topicConnection != null) topicConnection.close();
      if (queueSession != null) queueSession.close();
      if (queueConnection != null) queueConnection.close();
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
      ((ActiveMQMessage) message).setReadOnlyProperties(readOnlyProperties);
    }
  }
}

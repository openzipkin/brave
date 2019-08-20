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
import org.junit.rules.ExternalResource;
import org.junit.rules.TestName;

public abstract class JmsTestRule extends ExternalResource {

  final TestName testName;
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

  JmsTestRule(TestName testName) {
    this.testName = testName;
  }

  TextMessage newMessage(String text) throws JMSException {
    return queueSession.createTextMessage(text);
  }

  BytesMessage newBytesMessage(String text) throws JMSException {
    BytesMessage message = queueSession.createBytesMessage();
    message.writeUTF(text);
    return message;
  }

  abstract void setReadOnlyProperties(Message message, boolean readOnlyProperties) throws Exception;

  abstract Connection newConnection() throws Exception;

  abstract QueueConnection newQueueConnection() throws Exception;

  abstract TopicConnection newTopicConnection() throws Exception;

  @Override public void before() throws Exception {
    connection = newConnection();
    connection.start();
    // Pass redundant info as we can't user default method in activeMQ
    session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

    destinationName = testName.getMethodName() + "-d";
    destination = session.createQueue(destinationName);

    queueConnection = newQueueConnection();
    queueConnection.start();
    queueSession = queueConnection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);

    queueName = testName.getMethodName() + "-q";
    queue = queueSession.createQueue(queueName);

    topicConnection = newTopicConnection();
    topicConnection.start();
    topicSession = topicConnection.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);

    topicName = testName.getMethodName() + "-t";
    topic = topicSession.createTopic(topicName);
  }

  @Override public void after() {
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

  static class ActiveMQ extends JmsTestRule {
    ActiveMQ(TestName testName) {
      super(testName);
    }

    @Override Connection newConnection() throws Exception {
      return new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false")
        .createConnection();
    }

    @Override QueueConnection newQueueConnection() throws Exception {
      return new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false")
        .createQueueConnection();
    }

    @Override TopicConnection newTopicConnection() throws Exception {
      return new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false")
        .createTopicConnection();
    }

    @Override void setReadOnlyProperties(Message message, boolean readOnlyProperties) {
      ((ActiveMQMessage) message).setReadOnlyProperties(readOnlyProperties);
    }
  }
}

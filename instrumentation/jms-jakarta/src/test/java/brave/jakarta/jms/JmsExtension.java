/*
 * Copyright 2013-2024 The OpenZipkin Authors
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
import jakarta.jms.JMSContext;
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
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQMessage;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * ActiveMQ 6 supports JMS 2.0, but requires JDK 17. We have to build on JDK
 * 11, so use Artemis instead, despite it requiring Netty.
 *
 * <p>See https://issues.apache.org/jira/browse/AMQ-5736?focusedCommentId=16593091&page=com.atlassian.jira.plugin.system.issuetabpanels%3Acomment-tabpanel#comment-16593091
 */
public class JmsExtension implements BeforeEachCallback, AfterEachCallback {
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

  EmbeddedActiveMQ server = new EmbeddedActiveMQ();
  ActiveMQJMSConnectionFactory factory;
  AtomicBoolean started = new AtomicBoolean();

  JmsExtension() {
    factory = new ActiveMQJMSConnectionFactory("vm://0");
    factory.setProducerMaxRate(1); // to allow tests to use production order
  }

  void maybeStartServer() throws Exception {
    if (started.getAndSet(true)) return;
    // Configuration values from EmbeddedActiveMQResource
    server.setConfiguration(new ConfigurationImpl().setName(testName)
      .setPersistenceEnabled(false)
      .setSecurityEnabled(false)
      .setJMXManagementEnabled(false)
      .addAcceptorConfiguration(new TransportConfiguration(InVMAcceptorFactory.class.getName()))
      .addAddressSetting("#",
        new AddressSettings().setDeadLetterAddress(SimpleString.toSimpleString("dla"))
          .setExpiryAddress(SimpleString.toSimpleString("expiry"))));
    server.start();
  }

  JMSContext newContext() {
    return factory.createContext(JMSContext.AUTO_ACKNOWLEDGE);
  }

  Connection newConnection() throws Exception {
    maybeStartServer();
    return factory.createConnection();
  }

  QueueConnection newQueueConnection() throws Exception {
    maybeStartServer();
    return factory.createQueueConnection();
  }

  TopicConnection newTopicConnection() throws Exception {
    maybeStartServer();
    return factory.createTopicConnection();
  }

  void setReadOnlyProperties(Message message, boolean readOnlyProperties) {
    try {
      Field propertiesReadOnly = ActiveMQMessage.class.getDeclaredField("propertiesReadOnly");
      propertiesReadOnly.setAccessible(true);
      propertiesReadOnly.set(message, readOnlyProperties);
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  TextMessage newMessage(String text) throws JMSException {
    return queueSession.createTextMessage(text);
  }

  BytesMessage newBytesMessage(String text) throws JMSException {
    BytesMessage message = queueSession.createBytesMessage();
    message.writeUTF(text);
    return message;
  }

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
    factory.close();
    server.stop();
  }
}

package brave.jms;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.TopicSession;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQTextMessage;
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

  abstract void setReadOnlyProperties(TextMessage message, boolean readOnlyProperties)
      throws Exception;

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

    @Override void setReadOnlyProperties(TextMessage message, boolean readOnlyProperties) {
      ((ActiveMQTextMessage) message).setReadOnlyProperties(readOnlyProperties);
    }
  }
}

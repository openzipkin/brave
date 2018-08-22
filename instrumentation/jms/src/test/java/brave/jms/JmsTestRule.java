package brave.jms;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.QueueConnection;
import javax.jms.Session;
import javax.jms.TextMessage;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.junit.rules.ExternalResource;
import org.junit.rules.TestName;

public abstract class JmsTestRule extends ExternalResource {

  final TestName testName;
  QueueConnection connection;
  Session session;
  Destination queue, topic;
  String queueName, topicName;

  JmsTestRule(TestName testName) {
    this.testName = testName;
  }

  MessageProducer createQueueProducer() throws JMSException {
    return session.createProducer(queue);
  }

  MessageConsumer createQueueConsumer() throws JMSException {
    return session.createConsumer(queue);
  }

  TextMessage createTextMessage(String text) throws JMSException {
    return session.createTextMessage(text);
  }

  abstract void setReadOnlyProperties(TextMessage message, boolean readOnlyProperties)
      throws Exception;

  abstract QueueConnection newQueueConnection() throws Exception;

  @Override public void before() throws Exception {
    connection = newQueueConnection();
    connection.start();

    // Pass redundant info as we can't user default method in activeMQ
    session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
    queueName = testName.getMethodName() + "-q";
    queue = session.createQueue(queueName);
    topicName = testName.getMethodName() + "-t";
    topic = session.createQueue(topicName);
  }

  @Override public void after() {
    try {
      session.close();
      connection.close();
    } catch (JMSException e) {
      throw new AssertionError(e);
    }
  }

  static class ActiveMQ extends JmsTestRule {
    ActiveMQ(TestName testName) {
      super(testName);
    }

    @Override QueueConnection newQueueConnection() throws Exception {
      return new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false")
          .createQueueConnection();
    }

    @Override void setReadOnlyProperties(TextMessage message, boolean readOnlyProperties) {
      ((ActiveMQTextMessage) message).setReadOnlyProperties(readOnlyProperties);
    }
  }
}

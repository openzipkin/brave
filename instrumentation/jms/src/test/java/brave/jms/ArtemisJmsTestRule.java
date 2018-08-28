package brave.jms;

import java.lang.reflect.Field;
import javax.jms.JMSContext;
import javax.jms.QueueConnection;
import javax.jms.TextMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQMessage;
import org.apache.activemq.artemis.junit.EmbeddedJMSResource;
import org.junit.rules.TestName;

/**
 * Currently, regular activemq doesn't support JMS 2.0, so we need to use the one that requires
 * netty etc.
 *
 * <p>See https://issues.apache.org/jira/browse/AMQ-5736?focusedCommentId=16593091&page=com.atlassian.jira.plugin.system.issuetabpanels%3Acomment-tabpanel#comment-16593091
 */
class ArtemisJmsTestRule extends JmsTestRule {
  EmbeddedJMSResource resource = new EmbeddedJMSResource();
  ActiveMQJMSConnectionFactory factory;

  ArtemisJmsTestRule(TestName testName) {
    super(testName);
    factory = new ActiveMQJMSConnectionFactory("vm://0");
    factory.setProducerMaxRate(1); // to allow tests to use production order
  }

  JMSContext newContext() {
    return factory.createContext(JMSContext.AUTO_ACKNOWLEDGE);
  }

  @Override QueueConnection newQueueConnection() throws Exception {
    resource.start();
    return factory.createQueueConnection();
  }

  @Override void setReadOnlyProperties(TextMessage message, boolean readOnlyProperties)
      throws Exception {
    Field propertiesReadOnly = ActiveMQMessage.class.getDeclaredField("propertiesReadOnly");
    propertiesReadOnly.setAccessible(true);
    propertiesReadOnly.set(message, readOnlyProperties);
  }

  @Override public void after() {
    super.after();
    factory.close();
    resource.stop();
  }
}

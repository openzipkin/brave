/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jms;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.jms.Connection;
import javax.jms.JMSContext;
import javax.jms.Message;
import javax.jms.QueueConnection;
import javax.jms.TopicConnection;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQMessage;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Currently, regular activemq doesn't support JMS 2.0, so we need to use the one that requires
 * netty etc.
 *
 * <p>See https://issues.apache.org/jira/browse/AMQ-5736?focusedCommentId=16593091&page=com.atlassian.jira.plugin.system.issuetabpanels%3Acomment-tabpanel#comment-16593091
 */
class ArtemisJmsExtension extends JmsExtension {
  EmbeddedActiveMQ server = new EmbeddedActiveMQ();
  ActiveMQJMSConnectionFactory factory;
  AtomicBoolean started = new AtomicBoolean();

  ArtemisJmsExtension() {
    factory = new ActiveMQJMSConnectionFactory("vm://0");
    factory.setProducerMaxRate(1); // to allow tests to use production order
    factory.setReconnectAttempts(3); // to allow tests to reconnect on failure
  }

  void maybeStartServer() throws Exception {
    if (started.getAndSet(true)) return;
    // Configuration values from EmbeddedActiveMQResource
    server.setConfiguration(new ConfigurationImpl().setName(testName)
      .setPersistenceEnabled(false)
      .setSecurityEnabled(false)
      .addAcceptorConfiguration(new TransportConfiguration(InVMAcceptorFactory.class.getName()))
      .addAddressSetting("#",
        new AddressSettings().setDeadLetterAddress(SimpleString.toSimpleString("dla"))
          .setExpiryAddress(SimpleString.toSimpleString("expiry"))));
    server.start();
  }

  JMSContext newContext() {
    return factory.createContext(JMSContext.AUTO_ACKNOWLEDGE);
  }

  @Override Connection newConnection() throws Exception {
    maybeStartServer();
    return factory.createConnection();
  }

  @Override QueueConnection newQueueConnection() throws Exception {
    maybeStartServer();
    return factory.createQueueConnection();
  }

  @Override TopicConnection newTopicConnection() throws Exception {
    maybeStartServer();
    return factory.createTopicConnection();
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

  @Override public void afterEach(ExtensionContext context) throws Exception {
    super.afterEach(context);
    factory.close();
    server.stop();
  }
}

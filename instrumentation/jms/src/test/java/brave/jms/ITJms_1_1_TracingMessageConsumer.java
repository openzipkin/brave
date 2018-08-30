package brave.jms;

import java.util.Collections;
import java.util.Map;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.QueueReceiver;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.TopicPublisher;
import javax.jms.TopicSession;
import javax.jms.TopicSubscriber;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;

/** When adding tests here, also add to {@linkplain brave.jms.ITTracingJMSConsumer} */
public class ITJms_1_1_TracingMessageConsumer extends JmsTest {
  @Rule public TestName testName = new TestName();
  @Rule public JmsTestRule jms = newJmsTestRule(testName);

  Session tracedSession;
  MessageProducer messageProducer;
  MessageConsumer messageConsumer;

  QueueSession tracedQueueSession;
  QueueSender queueSender;
  QueueReceiver queueReceiver;

  TopicSession tracedTopicSession;
  TopicPublisher topicPublisher;
  TopicSubscriber topicSubscriber;

  TextMessage message;

  JmsTestRule newJmsTestRule(TestName testName) {
    return new JmsTestRule.ActiveMQ(testName);
  }

  @Before public void setup() throws Exception {
    tracedSession = jmsTracing.connection(jms.connection)
        .createSession(false, Session.AUTO_ACKNOWLEDGE);
    tracedQueueSession = jmsTracing.queueConnection(jms.queueConnection)
        .createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
    tracedTopicSession = jmsTracing.topicConnection(jms.topicConnection)
        .createTopicSession(false, Session.AUTO_ACKNOWLEDGE);

    messageProducer = jms.session.createProducer(jms.destination);
    messageConsumer = tracedSession.createConsumer(jms.destination);

    queueSender = jms.queueSession.createSender(jms.queue);
    queueReceiver = tracedQueueSession.createReceiver(jms.queue);

    topicPublisher = jms.topicSession.createPublisher(jms.topic);
    topicSubscriber = tracedTopicSession.createSubscriber(jms.topic);

    message = jms.newMessage("foo");
    // this forces us to handle JMS write concerns!
    jms.setReadOnlyProperties(message, true);
  }

  @After public void tearDownTraced() throws JMSException {
    tracedSession.close();
    tracedQueueSession.close();
    tracedTopicSession.close();
  }

  @Test public void messageListener_startsNewTrace() throws Exception {
    messageListener_startsNewTrace(
        () -> messageProducer.send(message),
        messageConsumer,
        Collections.singletonMap("jms.queue", jms.destinationName)
    );
  }

  @Test public void messageListener_startsNewTrace_queue() throws Exception {
    messageListener_startsNewTrace(
        () -> queueSender.send(message),
        queueReceiver,
        Collections.singletonMap("jms.queue", jms.queueName)
    );
  }

  @Test public void messageListener_startsNewTrace_topic() throws Exception {
    messageListener_startsNewTrace(
        () -> topicPublisher.send(message),
        topicSubscriber,
        Collections.singletonMap("jms.topic", jms.topicName)
    );
  }

  void messageListener_startsNewTrace(JMSRunnable send, MessageConsumer messageConsumer,
      Map<String, String> consumerTags) throws Exception {
    messageConsumer.setMessageListener(
        m -> tracing.tracer().currentSpanCustomizer().name("message-listener")
    );
    send.run();

    Span consumerSpan = takeSpan();
    assertThat(consumerSpan.name()).isEqualTo("receive");
    assertThat(consumerSpan.parentId()).isNull(); // root span
    assertThat(consumerSpan.kind()).isEqualTo(Span.Kind.CONSUMER);
    assertThat(consumerSpan.tags()).isEqualTo(consumerTags);

    Span listenerSpan = takeSpan();
    assertThat(listenerSpan.name()).isEqualTo("message-listener"); // overridden name
    assertThat(listenerSpan.parentId()).isEqualTo(consumerSpan.id()); // root span
    assertThat(listenerSpan.kind()).isNull(); // processor span, not a consumer
    assertThat(listenerSpan.tags()).isEmpty();
  }

  @Test public void messageListener_resumesTrace() throws Exception {
    messageListener_resumesTrace(() -> messageProducer.send(message), messageConsumer);
  }

  @Test public void messageListener_resumesTrace_queue() throws Exception {
    messageListener_resumesTrace(() -> queueSender.send(message), queueReceiver);
  }

  @Test public void messageListener_resumesTrace_topic() throws Exception {
    messageListener_resumesTrace(() -> topicPublisher.send(message), topicSubscriber);
  }

  void messageListener_resumesTrace(JMSRunnable send, MessageConsumer messageConsumer)
      throws Exception {
    messageConsumer.setMessageListener(m -> {
      try {
        // clearing headers ensures later work doesn't try to use the old parent
        assertThat(m.getStringProperty("b3")).isNull();
      } catch (JMSException e) {
        e.printStackTrace();
      }
    });

    String parentId = "463ac35c9f6413ad";
    jms.setReadOnlyProperties(message, false);
    message.setStringProperty("b3", parentId + "-" + parentId + "-1");
    send.run();

    Span consumerSpan = takeSpan();
    assertThat(consumerSpan.parentId()).isEqualTo(parentId);
    assertThat(takeSpan().parentId()).isEqualTo(consumerSpan.id()); // root span
  }

  @Test public void receive_startsNewTrace() throws Exception {
    receive_startsNewTrace(
        () -> messageProducer.send(message),
        messageConsumer,
        Collections.singletonMap("jms.queue", jms.destinationName)
    );
  }

  @Test public void receive_startsNewTrace_queue() throws Exception {
    receive_startsNewTrace(
        () -> queueSender.send(message),
        queueReceiver,
        Collections.singletonMap("jms.queue", jms.queueName)
    );
  }

  @Test public void receive_startsNewTrace_topic() throws Exception {
    receive_startsNewTrace(
        () -> topicPublisher.send(message),
        topicSubscriber,
        Collections.singletonMap("jms.topic", jms.topicName)
    );
  }

  void receive_startsNewTrace(JMSRunnable send, MessageConsumer messageConsumer,
      Map<String, String> consumerTags) throws Exception {
    send.run();

    messageConsumer.receive();

    Span consumerSpan = takeSpan();
    assertThat(consumerSpan.name()).isEqualTo("receive");
    assertThat(consumerSpan.parentId()).isNull(); // root span
    assertThat(consumerSpan.kind()).isEqualTo(Span.Kind.CONSUMER);
    assertThat(consumerSpan.tags()).isEqualTo(consumerTags);
  }

  @Test public void receive_resumesTrace() throws Exception {
    receive_resumesTrace(() -> messageProducer.send(message), messageConsumer);
  }

  @Test public void receive_resumesTrace_queue() throws Exception {
    receive_resumesTrace(() -> queueSender.send(message), queueReceiver);
  }

  @Test public void receive_resumesTrace_topic() throws Exception {
    receive_resumesTrace(() -> topicPublisher.send(message), topicSubscriber);
  }

  void receive_resumesTrace(JMSRunnable send, MessageConsumer messageConsumer) throws Exception {
    String parentId = "463ac35c9f6413ad";
    jms.setReadOnlyProperties(message, false);
    message.setStringProperty("b3", parentId + "-" + parentId + "-1");
    send.run();

    Message received = messageConsumer.receive();
    Span consumerSpan = takeSpan();
    assertThat(consumerSpan.parentId()).isEqualTo(parentId);

    assertThat(received.getStringProperty("b3"))
        .isEqualTo(parentId + "-" + consumerSpan.id() + "-1");
  }
}

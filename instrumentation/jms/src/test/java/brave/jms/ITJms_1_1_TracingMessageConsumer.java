package brave.jms;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.TextMessage;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;

public class ITJms_1_1_TracingMessageConsumer extends JmsTest {
  @Rule public TestName testName = new TestName();
  @Rule public JmsTestRule jms = newJmsTestRule(testName);

  MessageProducer producer;
  MessageConsumer consumer;
  TextMessage message;

  JmsTestRule newJmsTestRule(TestName testName) {
    return new JmsTestRule.ActiveMQ(testName);
  }

  @Before public void setup() throws Exception {
    producer = jms.createQueueProducer();
    consumer = jmsTracing.messageConsumer(jms.createQueueConsumer());
    message = jms.createTextMessage("foo");
    // this forces us to handle JMS write concerns!
    jms.setReadOnlyProperties(message, true);
  }

  @Test public void messageListener_startsNewTrace() throws Exception {
    consumer.setMessageListener(
        m -> tracing.tracer().currentSpanCustomizer().name("message-listener")
    );

    producer.send(message);

    Span consumerSpan = takeSpan();
    assertThat(consumerSpan.name()).isEqualTo("receive");
    assertThat(consumerSpan.parentId()).isNull(); // root span
    assertThat(consumerSpan.kind()).isEqualTo(Span.Kind.CONSUMER);
    assertThat(consumerSpan.tags().get("jms.destination")).contains(jms.queueName);

    Span listenerSpan = takeSpan();
    assertThat(listenerSpan.name()).isEqualTo("message-listener"); // overridden name
    assertThat(listenerSpan.parentId()).isEqualTo(consumerSpan.id()); // root span
    assertThat(listenerSpan.kind()).isNull(); // processor span, not a consumer
    assertThat(listenerSpan.tags()).isEmpty();
  }

  @Test public void messageListener_resumesTrace() throws Exception {
    consumer.setMessageListener(m -> {
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
    producer.send(message);

    Span consumerSpan = takeSpan();
    assertThat(consumerSpan.parentId()).isEqualTo(parentId);
    assertThat(takeSpan().parentId()).isEqualTo(consumerSpan.id()); // root span
  }

  @Test public void receive_startsNewTrace() throws Exception {
    producer.send(message);

    consumer.receive();

    Span consumerSpan = takeSpan();
    assertThat(consumerSpan.name()).isEqualTo("receive");
    assertThat(consumerSpan.parentId()).isNull(); // root span
    assertThat(consumerSpan.kind()).isEqualTo(Span.Kind.CONSUMER);
    assertThat(consumerSpan.tags().get("jms.destination")).contains(jms.queueName);
  }

  @Test public void receive_resumesTrace() throws Exception {
    String parentId = "463ac35c9f6413ad";
    jms.setReadOnlyProperties(message, false);
    message.setStringProperty("b3", parentId + "-" + parentId + "-1");
    producer.send(message);

    Message received = consumer.receive();
    Span consumerSpan = takeSpan();
    assertThat(consumerSpan.parentId()).isEqualTo(parentId);

    assertThat(received.getStringProperty("b3"))
        .isEqualTo(parentId + "-" + consumerSpan.id() + "-1");
  }
}

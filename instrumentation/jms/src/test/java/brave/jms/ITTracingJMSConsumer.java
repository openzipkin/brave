package brave.jms;

import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.JMSProducer;
import javax.jms.Message;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;

/** When adding tests here, also add to {@linkplain brave.jms.ITJms_2_0_TracingMessageConsumer} */
public class ITTracingJMSConsumer extends JmsTest {
  @Rule public TestName testName = new TestName();
  @Rule public ArtemisJmsTestRule jms = new ArtemisJmsTestRule(testName);

  JMSContext tracedContext;
  JMSProducer producer;
  JMSConsumer consumer;
  JMSContext context;

  @Before public void setup() {
    context = jms.newContext();
    tracedContext = jmsTracing.connectionFactory(jms.factory)
        .createContext(JMSContext.AUTO_ACKNOWLEDGE);

    producer = context.createProducer();
    consumer = tracedContext.createConsumer(jms.queue);
  }

  @After public void tearDownTraced() {
    tracedContext.close();
  }

  @Test public void messageListener_startsNewTrace() throws Exception {
    consumer.setMessageListener(
        m -> tracing.tracer().currentSpanCustomizer().name("message-listener")
    );

    producer.send(jms.queue, "foo");

    Span consumerSpan = takeSpan();
    assertThat(consumerSpan.name()).isEqualTo("receive");
    assertThat(consumerSpan.parentId()).isNull(); // root span
    assertThat(consumerSpan.kind()).isEqualTo(Span.Kind.CONSUMER);
    assertThat(consumerSpan.tags()).containsEntry("jms.queue", jms.queueName);

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
    producer.setProperty("b3", parentId + "-" + parentId + "-1");
    producer.send(jms.queue, "foo");

    Span consumerSpan = takeSpan();
    assertThat(consumerSpan.parentId()).isEqualTo(parentId);
    assertThat(takeSpan().parentId()).isEqualTo(consumerSpan.id()); // root span
  }

  @Test public void receive_startsNewTrace() throws Exception {
    producer.send(jms.queue, "foo");
    consumer.receive();

    Span consumerSpan = takeSpan();
    assertThat(consumerSpan.name()).isEqualTo("receive");
    assertThat(consumerSpan.parentId()).isNull(); // root span
    assertThat(consumerSpan.kind()).isEqualTo(Span.Kind.CONSUMER);
    assertThat(consumerSpan.tags()).containsEntry("jms.queue", jms.queueName);
  }

  @Test public void receive_resumesTrace() throws Exception {
    String parentId = "463ac35c9f6413ad";
    producer.setProperty("b3", parentId + "-" + parentId + "-1");
    producer.send(jms.queue, "foo");

    Message received = consumer.receive();
    Span consumerSpan = takeSpan();
    assertThat(consumerSpan.parentId()).isEqualTo(parentId);

    assertThat(received.getStringProperty("b3"))
        .isEqualTo(parentId + "-" + consumerSpan.id() + "-1");
  }
}

package brave.jms;

import brave.ScopedSpan;
import brave.propagation.TraceContext;
import java.util.Collections;
import java.util.Map;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import zipkin2.Span;

import static brave.propagation.B3SingleFormat.writeB3SingleFormat;
import static org.assertj.core.api.Assertions.assertThat;

/** When adding tests here, also add to {@linkplain brave.jms.ITTracingJMSProducer} */
public class ITJms_1_1_TracingMessageProducer extends JmsTest {
  @Rule public TestName testName = new TestName();
  @Rule public JmsTestRule jms = newJmsTestRule(testName);

  Session tracedSession;
  MessageProducer producer;
  MessageConsumer consumer;
  TextMessage message;
  Map<String, String> existingProperties = Collections.singletonMap("tx", "1");

  JmsTestRule newJmsTestRule(TestName testName) {
    return new JmsTestRule.ActiveMQ(testName);
  }

  @Before public void setup() throws Exception {
    tracedSession = jmsTracing.connection(jms.connection)
        .createSession(false, Session.AUTO_ACKNOWLEDGE);

    producer = tracedSession.createProducer(jms.queue);
    consumer = jms.createQueueConsumer();
    message = jms.createTextMessage("foo");
    for (Map.Entry<String, String> existingProperty : existingProperties.entrySet()) {
      message.setStringProperty(existingProperty.getKey(), existingProperty.getValue());
    }
    // this forces us to handle JMS write concerns!
    jms.setReadOnlyProperties(message, true);
  }

  @After public void tearDownTraced() throws JMSException {
    tracedSession.close();
  }

  @Test public void should_add_b3_single_property() throws Exception {
    producer.send(message);

    Message received = consumer.receive();
    Span producerSpan = takeSpan();

    assertThat(propertiesToMap(received))
        .containsAllEntriesOf(existingProperties)
        .containsEntry("b3", producerSpan.traceId() + "-" + producerSpan.id() + "-1");
  }

  @Test public void should_not_serialize_parent_span_id() throws Exception {
    ScopedSpan parent = tracing.tracer().startScopedSpan("main");
    try {
      producer.send(message);
    } finally {
      parent.finish();
    }

    Message received = consumer.receive();
    Span producerSpan = takeSpan(), parentSpan = takeSpan();
    assertThat(producerSpan.parentId()).isEqualTo(parentSpan.id());

    assertThat(propertiesToMap(received))
        .containsAllEntriesOf(existingProperties)
        .containsEntry("b3", producerSpan.traceId() + "-" + producerSpan.id() + "-1");
  }

  @Test public void should_prefer_current_to_stale_b3_header() throws Exception {
    jms.setReadOnlyProperties(message, false);
    message.setStringProperty("b3",
        writeB3SingleFormat(TraceContext.newBuilder().traceId(1).spanId(1).build()));

    ScopedSpan parent = tracing.tracer().startScopedSpan("main");
    try {
      producer.send(message);
    } finally {
      parent.finish();
    }

    Message received = consumer.receive();
    Span producerSpan = takeSpan(), parentSpan = takeSpan();
    assertThat(producerSpan.parentId()).isEqualTo(parentSpan.id());

    assertThat(propertiesToMap(received))
        .containsAllEntriesOf(existingProperties)
        .containsEntry("b3", producerSpan.traceId() + "-" + producerSpan.id() + "-1");
  }

  @Test public void should_record_properties() throws Exception {
    producer.send(message);

    consumer.receive();

    Span producerSpan = takeSpan();
    assertThat(producerSpan.name()).isEqualTo("send");
    assertThat(producerSpan.kind()).isEqualTo(Span.Kind.PRODUCER);
    assertThat(producerSpan.timestampAsLong()).isPositive();
    assertThat(producerSpan.durationAsLong()).isPositive();
    assertThat(producerSpan.tags()).containsEntry("jms.queue", jms.queueName);
  }

  @Test public void should_record_error() throws Exception {
    tracedSession.close();

    try {
      producer.send(message);
    } catch (Exception e) {
    }

    assertThat(takeSpan().tags()).containsKey("error");
  }
}

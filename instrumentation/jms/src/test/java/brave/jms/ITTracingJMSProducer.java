package brave.jms;

import brave.ScopedSpan;
import brave.propagation.TraceContext;
import java.util.Collections;
import java.util.Map;
import javax.jms.CompletionListener;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSProducer;
import javax.jms.Message;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import zipkin2.Span;

import static brave.propagation.B3SingleFormat.writeB3SingleFormat;
import static org.assertj.core.api.Assertions.assertThat;

/** When adding tests here, also add to {@linkplain brave.jms.ITJms_2_0_TracingMessageProducer} */
public class ITTracingJMSProducer extends JmsTest {
  @Rule public TestName testName = new TestName();
  @Rule public ArtemisJmsTestRule jms = new ArtemisJmsTestRule(testName);

  JMSContext tracedContext;
  JMSProducer producer;
  JMSConsumer consumer;
  JMSContext context;
  Map<String, String> existingProperties = Collections.singletonMap("tx", "1");

  @Before public void setup() {
    context = jms.newContext();
    tracedContext = jmsTracing.connectionFactory(jms.factory)
        .createContext(JMSContext.AUTO_ACKNOWLEDGE);

    producer = tracedContext.createProducer();
    existingProperties.forEach(producer::setProperty);
    consumer = context.createConsumer(jms.queue);
  }

  @After public void tearDownTraced() {
    tracedContext.close();
  }

  @Test public void should_add_b3_single_property() throws Exception {
    producer.send(jms.queue, "foo");

    Message received = consumer.receive();
    Span producerSpan = takeSpan();

    assertThat(propertiesToMap(received))
        .containsAllEntriesOf(existingProperties)
        .containsEntry("b3", producerSpan.traceId() + "-" + producerSpan.id() + "-1");
  }

  @Test public void should_not_serialize_parent_span_id() throws Exception {
    ScopedSpan parent = tracing.tracer().startScopedSpan("main");
    try {
      producer.send(jms.queue, "foo");
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
    producer.setProperty("b3",
        writeB3SingleFormat(TraceContext.newBuilder().traceId(1).spanId(1).build()));

    ScopedSpan parent = tracing.tracer().startScopedSpan("main");
    try {
      producer.send(jms.queue, "foo");
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
    producer.send(jms.queue, "foo");

    consumer.receive();

    Span producerSpan = takeSpan();
    assertThat(producerSpan.name()).isEqualTo("send");
    assertThat(producerSpan.kind()).isEqualTo(Span.Kind.PRODUCER);
    assertThat(producerSpan.timestampAsLong()).isPositive();
    assertThat(producerSpan.durationAsLong()).isPositive();
    assertThat(producerSpan.tags()).containsEntry("jms.queue", jms.queueName);
  }

  @Test public void should_record_error() throws Exception {
    jms.after();

    try {
      producer.send(jms.queue, "foo");
    } catch (Exception e) {
    }

    assertThat(takeSpan().tags()).containsKey("error");
  }

  @Test public void should_complete_on_callback() throws Exception {
    producer.setAsync(new CompletionListener() {
      @Override public void onCompletion(Message message) {
        tracing.tracer().currentSpanCustomizer().tag("onCompletion", "");
      }

      @Override public void onException(Message message, Exception exception) {
        tracing.tracer().currentSpanCustomizer().tag("onException", "");
      }
    });
    producer.send(jms.queue, "foo");

    Span producerSpan = takeSpan();
    assertThat(producerSpan.timestampAsLong()).isPositive();
    assertThat(producerSpan.durationAsLong()).isPositive();
    assertThat(producerSpan.tags()).containsKeys("onCompletion");
  }

  // TODO: find a way to test error callbacks. See ITJms_2_0_TracingMessageProducer.should_complete_on_error_callback
}

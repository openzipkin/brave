package brave.jms;

import brave.Tracing;
import brave.propagation.ThreadLocalCurrentTraceContext;
import java.util.ArrayList;
import java.util.List;
import javax.jms.Message;
import javax.jms.MessageListener;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.junit.After;
import org.junit.Test;
import zipkin2.Span;

import static org.apache.activemq.command.ActiveMQDestination.QUEUE_TYPE;
import static org.apache.activemq.command.ActiveMQDestination.createDestination;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static zipkin2.Span.Kind.CONSUMER;

// ported from TracingRabbitListenerAdviceTest
public class TracingMessageListenerTest {

  static String TRACE_ID = "463ac35c9f6413ad";
  static String PARENT_ID = "463ac35c9f6413ab";
  static String SPAN_ID = "48485a3953bb6124";
  static String SAMPLED = "1";

  List<Span> spans = new ArrayList<>();
  Tracing tracing = Tracing.newBuilder()
      .currentTraceContext(ThreadLocalCurrentTraceContext.create())
      .spanReporter(spans::add)
      .build();

  MessageListener delegate = mock(MessageListener.class);
  MessageListener tracingMessageListener =
      new TracingMessageListener(delegate, JmsTracing.newBuilder(tracing)
          .remoteServiceName("my-service")
          .build());

  @After public void close() {
    tracing.close();
  }

  @Test public void starts_new_trace_if_none_exists() throws Exception {
    ActiveMQTextMessage message = new ActiveMQTextMessage();
    onMessageConsumed(message);

    assertThat(spans)
        .extracting(Span::kind)
        .containsExactly(CONSUMER, null);
  }

  @Test public void consumer_and_listener_have_names() throws Exception {
    ActiveMQTextMessage message = new ActiveMQTextMessage();
    onMessageConsumed(message);

    assertThat(spans)
        .extracting(Span::name)
        .containsExactly("receive", "on-message");
  }

  @Test public void consumer_has_remote_service_name() throws Exception {
    ActiveMQTextMessage message = new ActiveMQTextMessage();
    onMessageConsumed(message);

    assertThat(spans)
        .extracting(Span::remoteServiceName)
        .containsExactly("my-service", null);
  }

  @Test public void tags_consumer_span_but_not_listener() throws Exception {
    ActiveMQTextMessage message = new ActiveMQTextMessage();
    message.setDestination(createDestination("foo", QUEUE_TYPE));
    onMessageConsumed(message);

    assertThat(spans)
        .extracting(Span::kind)
        .containsExactly(CONSUMER, null);

    assertThat(spans.get(0).tags()).containsEntry("jms.queue", "foo");
    assertThat(spans.get(1).tags()).isEmpty();
  }

  @Test public void consumer_span_starts_before_listener() throws Exception {
    ActiveMQTextMessage message = new ActiveMQTextMessage();
    onMessageConsumed(message);

    // make sure one before the other
    assertThat(spans.get(0).timestampAsLong())
        .isLessThan(spans.get(1).timestampAsLong());

    // make sure they finished
    assertThat(spans.get(0).durationAsLong())
        .isPositive();
    assertThat(spans.get(1).durationAsLong())
        .isPositive();
  }

  @Test public void continues_parent_trace() throws Exception {
    ActiveMQTextMessage message = new ActiveMQTextMessage();
    message.setStringProperty("X-B3-TraceId", TRACE_ID);
    message.setStringProperty("X-B3-SpanId", SPAN_ID);
    message.setStringProperty("X-B3-ParentSpanId", PARENT_ID);
    message.setStringProperty("X-B3-Sampled", SAMPLED);

    onMessageConsumed(message);

    // clearing headers ensures later work doesn't try to use the old parent
    assertThat(message.getProperties()).isEmpty();

    assertThat(spans)
        .filteredOn(span -> span.kind() == CONSUMER)
        .extracting(Span::parentId)
        .contains(SPAN_ID);
  }

  @Test public void continues_parent_trace_single_header() throws Exception {
    ActiveMQTextMessage message = new ActiveMQTextMessage();
    message.setStringProperty("b3", TRACE_ID + "-" + SPAN_ID + "-" + SAMPLED);

    onMessageConsumed(message);

    // clearing headers ensures later work doesn't try to use the old parent
    assertThat(message.getProperties()).isEmpty();

    assertThat(spans)
        .filteredOn(span -> span.kind() == CONSUMER)
        .extracting(Span::parentId)
        .contains(SPAN_ID);
  }

  @Test public void reports_span_if_consume_fails() throws Exception {
    ActiveMQTextMessage message = new ActiveMQTextMessage();
    onMessageConsumeFailed(message, new RuntimeException("expected exception"));

    assertThat(spans)
        .extracting(Span::kind)
        .containsExactly(CONSUMER, null);

    assertThat(spans)
        .filteredOn(span -> span.kind() == null)
        .extracting(Span::tags)
        .extracting(tags -> tags.get("error"))
        .contains("expected exception");
  }

  @Test public void reports_span_if_consume_fails_with_no_message() throws Exception {
    ActiveMQTextMessage message = new ActiveMQTextMessage();
    onMessageConsumeFailed(message, new RuntimeException());

    assertThat(spans)
        .extracting(Span::kind)
        .containsExactly(CONSUMER, null);

    assertThat(spans)
        .filteredOn(span -> span.kind() == null)
        .extracting(Span::tags)
        .extracting(tags -> tags.get("error"))
        .containsOnly("RuntimeException");
  }

  void onMessageConsumed(Message message) throws Exception {
    doNothing().when(delegate).onMessage(message);
    tracingMessageListener.onMessage(message);
  }

  void onMessageConsumeFailed(Message message, Throwable throwable) throws Exception {
    doThrow(throwable).when(delegate).onMessage(message);

    try {
      tracingMessageListener.onMessage(message);
      fail("should have thrown exception");
    } catch (RuntimeException ex) {
    }
  }
}

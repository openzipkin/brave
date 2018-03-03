package brave.spring.rabbit;

import brave.Tracing;
import brave.sampler.Sampler;
import java.util.ArrayList;
import java.util.List;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.Before;
import org.junit.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static zipkin2.Span.Kind.CONSUMER;
import static zipkin2.Span.Kind.SERVER;

public class TracingRabbitListenerAdviceTest {

  private static String TRACE_ID = "463ac35c9f6413ad";
  private static String PARENT_ID = "463ac35c9f6413ab";
  private static String SPAN_ID = "48485a3953bb6124";
  private static String SAMPLED = "1";

  private List<Span> reportedSpans = new ArrayList<>();
  private TracingRabbitListenerAdvice tracingRabbitListenerAdvice;
  private MethodInvocation methodInvocation;

  @Before
  public void setupTracing() {
    reportedSpans.clear();
    Tracing tracing = Tracing.newBuilder()
        .sampler(Sampler.ALWAYS_SAMPLE)
        .spanReporter(reportedSpans::add)
        .build();
    tracingRabbitListenerAdvice = new TracingRabbitListenerAdvice(tracing);

    methodInvocation = mock(MethodInvocation.class);
  }

  @Test
  public void starts_new_trace_if_none_exists() throws Throwable {
    Message message = MessageBuilder.withBody(new byte[] {}).build();
    onMessageConsumed(message);

    assertThat(reportedSpans)
        .extracting(Span::kind)
        .containsExactly(CONSUMER, null);
  }

  @Test
  public void continues_parent_trace() throws Throwable {
    MessageProperties props = new MessageProperties();
    props.setHeader("X-B3-TraceId", TRACE_ID);
    props.setHeader("X-B3-SpanId", SPAN_ID);
    props.setHeader("X-B3-ParentSpanId", PARENT_ID);
    props.setHeader("X-B3-Sampled", SAMPLED);

    Message message = MessageBuilder.withBody(new byte[] {})
        .andProperties(props)
        .build();
    onMessageConsumed(message);

    assertThat(reportedSpans)
        .filteredOn(span -> span.kind() == CONSUMER)
        .extracting(Span::parentId)
        .contains(SPAN_ID);
  }

  @Test
  public void reports_span_if_consume_fails() throws Throwable {
    Message message = MessageBuilder.withBody(new byte[] {}).build();
    onMessageConsumeFailed(message, new RuntimeException("expected exception"));

    assertThat(reportedSpans)
        .extracting(Span::kind)
        .containsExactly(CONSUMER, null);

    assertThat(reportedSpans)
        .filteredOn(span -> span.kind() == null)
        .extracting(Span::tags)
        .extracting(tags -> tags.get("error"))
        .contains("expected exception");
  }

  @Test
  public void reports_span_if_consume_fails_with_no_message() throws Throwable {
    Message message = MessageBuilder.withBody(new byte[] {}).build();
    onMessageConsumeFailed(message, new RuntimeException());

    assertThat(reportedSpans)
        .extracting(Span::kind)
        .containsExactly(CONSUMER, null);

    assertThat(reportedSpans)
        .filteredOn(span -> span.kind() == null)
        .extracting(Span::tags)
        .extracting(tags -> tags.get("error"))
        .contains("RuntimeException");
  }

  private void onMessageConsumed(Message message) throws Throwable {
    when(methodInvocation.getArguments()).thenReturn(new Object[] {
        null, // AMQPChannel - doesn't matter
        message
    });
    when(methodInvocation.proceed()).thenReturn("doesn't matter");

    tracingRabbitListenerAdvice.invoke(methodInvocation);
  }

  private void onMessageConsumeFailed(Message message, Throwable throwable) throws Throwable {
    when(methodInvocation.getArguments()).thenReturn(new Object[] {
        null, // AMQPChannel - doesn't matter
        message
    });
    when(methodInvocation.proceed()).thenThrow(throwable);

    try {
      tracingRabbitListenerAdvice.invoke(methodInvocation);
      fail("should have thrown exception");
    } catch (RuntimeException ex) {

    }
  }
}
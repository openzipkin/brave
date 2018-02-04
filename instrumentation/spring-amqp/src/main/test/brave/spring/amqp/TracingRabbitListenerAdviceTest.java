package brave.spring.amqp;

import brave.Tracing;
import brave.sampler.Sampler;
import java.util.ArrayList;
import java.util.List;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.Before;
import org.junit.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TracingRabbitListenerAdviceTest {

  private List<Span> reportedSpans = new ArrayList<>();
  private TracingRabbitListenerAdvice tracingRabbitListenerAdvice;

  @Before
  public void setupTracing() {
    reportedSpans.clear();
    Tracing tracing = Tracing.newBuilder()
        .sampler(Sampler.ALWAYS_SAMPLE)
        .spanReporter(reportedSpans::add)
        .build();
    tracingRabbitListenerAdvice = new TracingRabbitListenerAdvice(tracing);
  }

  @Test
  public void starts_new_trace_if_none_exists() throws Throwable {
    Message message = MessageBuilder.withBody(new byte[] {}).build();

    MethodInvocation methodInvocation = mock(MethodInvocation.class);

    when(methodInvocation.getArguments()).thenReturn(new Object[] {
        null, // AMQPChannel - doesn't matter
        message
    });
    when(methodInvocation.proceed()).thenReturn("doesn't matter");

    tracingRabbitListenerAdvice.invoke(methodInvocation);

    assertThat(reportedSpans)
        .extracting(Span::kind)
        .containsExactly(Span.Kind.CONSUMER);
  }
}
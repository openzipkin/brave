package brave.spring.rabbit;

import brave.Tracing;
import brave.propagation.StrictCurrentTraceContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.After;
import org.junit.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;

public class TracingMessagePostProcessorTest {
  List<Span> spans = new ArrayList<>();
  Tracing tracing = Tracing.newBuilder()
      .currentTraceContext(new StrictCurrentTraceContext())
      .spanReporter(spans::add)
      .build();
  TracingMessagePostProcessor tracingMessagePostProcessor =
      new TracingMessagePostProcessor(tracing, "my-exchange");

  @After public void close() {
    tracing.close();
  }

  @Test public void should_add_b3_headers_to_message() {
    Message message = MessageBuilder.withBody(new byte[] {}).build();
    Message postProcessMessage = tracingMessagePostProcessor.postProcessMessage(message);

    List<String> expectedHeaders = Arrays.asList("X-B3-TraceId", "X-B3-SpanId", "X-B3-Sampled");
    Set<String> headerKeys = postProcessMessage.getMessageProperties().getHeaders().keySet();

    assertThat(headerKeys).containsAll(expectedHeaders);
  }

  @Test public void should_report_span() {
    Message message = MessageBuilder.withBody(new byte[] {}).build();
    tracingMessagePostProcessor.postProcessMessage(message);

    assertThat(spans).hasSize(1);
  }

  @Test public void should_set_remote_service() {
    Message message = MessageBuilder.withBody(new byte[] {}).build();
    tracingMessagePostProcessor.postProcessMessage(message);

    assertThat(spans.get(0).remoteServiceName())
        .isEqualTo("my-exchange");
  }
}

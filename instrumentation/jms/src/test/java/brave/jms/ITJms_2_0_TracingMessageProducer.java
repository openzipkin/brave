package brave.jms;

import java.util.concurrent.CountDownLatch;
import javax.jms.CompletionListener;
import javax.jms.Message;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.rules.TestName;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;

public class ITJms_2_0_TracingMessageProducer extends ITJms_1_1_TracingMessageProducer {

  @Override JmsTestRule newJmsTestRule(TestName testName) {
    return new ArtemisJmsTestRule(testName);
  }

  @Test public void should_complete_on_callback() throws Exception {
    producer.send(message, new CompletionListener() {
      @Override public void onCompletion(Message message) {
        tracing.tracer().currentSpanCustomizer().tag("onCompletion", "");
      }

      @Override public void onException(Message message, Exception exception) {
        tracing.tracer().currentSpanCustomizer().tag("onException", "");
      }
    });

    Span producerSpan = takeSpan();
    assertThat(producerSpan.timestampAsLong()).isPositive();
    assertThat(producerSpan.durationAsLong()).isPositive();
    assertThat(producerSpan.tags()).containsKeys("onCompletion");
  }

  @Test
  @Ignore("https://issues.apache.org/jira/browse/ARTEMIS-2054")
  public void should_complete_on_error_callback() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);

    // To force error to be on callback thread, we need to wait until message is
    // queued. Only then, shutdown the session.
    jms.createQueueProducer().send(message, new CompletionListener() {
      @Override public void onCompletion(Message message) {
        try {
          latch.await();
        } catch (InterruptedException e) {
        }
      }

      @Override public void onException(Message message, Exception exception) {
      }
    });

    // If we hang here, this means the above send is not actually non-blocking!
    // Assuming messages are sent sequentially in a queue, the below should block until the forme
    // went through.
    producer.send(message, new CompletionListener() {
      @Override public void onCompletion(Message message) {
        tracing.tracer().currentSpanCustomizer().tag("onCompletion", "");
      }

      @Override public void onException(Message message, Exception exception) {
        tracing.tracer().currentSpanCustomizer().tag("onException", "");
      }
    });

    jms.after();
    latch.countDown();

    Span producerSpan = takeSpan();
    assertThat(producerSpan.timestampAsLong()).isPositive();
    assertThat(producerSpan.durationAsLong()).isPositive();
    assertThat(producerSpan.tags()).containsKeys("error", "onException");
  }
}

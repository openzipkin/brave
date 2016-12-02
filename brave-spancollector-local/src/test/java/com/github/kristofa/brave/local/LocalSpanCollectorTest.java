package com.github.kristofa.brave.local;

import com.github.kristofa.brave.SpanCollectorMetricsHandler;
import com.twitter.zipkin.gen.Span;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import zipkin.storage.AsyncSpanConsumer;
import zipkin.storage.AsyncSpanStore;
import zipkin.storage.InMemoryStorage;
import zipkin.storage.SpanStore;
import zipkin.storage.StorageComponent;

import static org.assertj.core.api.Assertions.assertThat;

public class LocalSpanCollectorTest {
  public final InMemoryStorage storage = new InMemoryStorage();

  TestMetricsHander metrics = new TestMetricsHander();
  // set flush interval to 0 so that tests can drive flushing explicitly
  LocalSpanCollector.Config config = LocalSpanCollector.Config.builder().flushInterval(0).build();

  @Test
  public void collectDoesntDoIO() throws Exception {
    LocalSpanCollector collector = newLocalSpanCollector((spans, callback) -> {
      throw new AssertionError("spans should only report on flush!");
    });

    collector.collect(span(1L, "foo"));

    assertThat(storage.spanStore().traceIds()).isEmpty();
  }

  @Test
  public void collectIncrementsAcceptedMetrics() throws Exception {
    LocalSpanCollector collector = newLocalSpanCollector((spans, callback) -> {
    });

    collector.collect(span(1L, "foo"));

    assertThat(metrics.acceptedSpans.get()).isEqualTo(1);
    assertThat(metrics.droppedSpans.get()).isZero();
  }

  @Test
  public void dropsWhenQueueIsFull() throws Exception {
    LocalSpanCollector collector = newLocalSpanCollector((spans, callback) -> {
    });

    for (int i = 0; i < 1001; i++)
      collector.collect(span(1L, "foo"));

    collector.flush(); // manually flush the spans

    assertThat(metrics.acceptedSpans.get()).isEqualTo(1001);
    assertThat(metrics.droppedSpans.get()).isEqualTo(1);
  }

  @Test
  public void bundlesSpansIntoOneMessage() throws Exception {
    AtomicInteger spanCount = new AtomicInteger();
    AtomicInteger messageCount = new AtomicInteger();
    LocalSpanCollector collector = newLocalSpanCollector((spans, callback) -> {
      spanCount.addAndGet(spans.size());
      messageCount.incrementAndGet();
    });

    collector.collect(span(1L, "foo"));
    collector.collect(span(2L, "bar"));

    collector.flush(); // manually flush the spans

    assertThat(spanCount.get()).isEqualTo(2);
    assertThat(messageCount.get()).isEqualTo(1);
  }

  @Test
  public void incrementsDroppedSpans_exceptionOnCallingThread() throws Exception {
    LocalSpanCollector collector = newLocalSpanCollector((spans, callback) -> {
      throw new RuntimeException("couldn't store");
    });

    collector.collect(span(1L, "foo"));
    collector.collect(span(2L, "bar"));

    collector.flush(); // manually flush the spans

    assertThat(metrics.droppedSpans.get()).isEqualTo(2);
  }

  @Test
  public void incrementsDroppedSpans_exceptionOnCallbackThread() throws Exception {
    LocalSpanCollector collector = newLocalSpanCollector((spans, callback) ->
        callback.onError(new RuntimeException("couldn't store")));

    collector.collect(span(1L, "foo"));
    collector.collect(span(2L, "bar"));

    collector.flush(); // manually flush the spans

    assertThat(metrics.droppedSpans.get()).isEqualTo(2);
  }

  class TestMetricsHander implements SpanCollectorMetricsHandler {

    final AtomicInteger acceptedSpans = new AtomicInteger();
    final AtomicInteger droppedSpans = new AtomicInteger();

    @Override
    public void incrementAcceptedSpans(int quantity) {
      acceptedSpans.addAndGet(quantity);
    }

    @Override
    public void incrementDroppedSpans(int quantity) {
      droppedSpans.addAndGet(quantity);
    }
  }

  static Span span(long traceId, String spanName) {
    return new Span().setTrace_id(traceId).setId(traceId).setName(spanName);
  }

  static zipkin.Span zipkinSpan(long traceId, String spanName) {
    return zipkin.Span.builder().traceId(traceId).id(traceId).name(spanName).build();
  }

  LocalSpanCollector newLocalSpanCollector(AsyncSpanConsumer consumer) {
    return new LocalSpanCollector(new StorageComponent() {
      @Override public SpanStore spanStore() {
        throw new AssertionError();
      }

      @Override public AsyncSpanStore asyncSpanStore() {
        throw new AssertionError();
      }

      @Override public AsyncSpanConsumer asyncSpanConsumer() {
        return consumer;
      }

      @Override public CheckResult check() {
        return CheckResult.OK;
      }

      @Override public void close() {
        throw new AssertionError();
      }
    }, config, metrics);
  }
}

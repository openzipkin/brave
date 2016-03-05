package com.github.kristofa.brave.http;

import com.github.kristofa.brave.SpanCollectorMetricsHandler;
import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.Span;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Rule;
import org.junit.Test;
import zipkin.junit.HttpFailure;
import zipkin.junit.ZipkinRule;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class HttpSpanCollectorTest {

  @Rule
  public final ZipkinRule zipkin = new ZipkinRule();

  TestMetricsHander metrics = new TestMetricsHander();
  // set flush interval to 0 so that tests can drive flushing explicitly
  HttpSpanCollector.Config config = HttpSpanCollector.Config.builder().flushInterval(0).build();
  HttpSpanCollector collector = new HttpSpanCollector(zipkin.httpUrl(), config, metrics);

  @Test
  public void collectDoesntDoIO() throws Exception {
    collector.collect(span(1L, "foo"));

    assertThat(zipkin.httpRequestCount()).isZero();
  }

  @Test
  public void collectIncrementsAcceptedMetrics() throws Exception {
    collector.collect(span(1L, "foo"));

    assertThat(metrics.acceptedSpans.get()).isEqualTo(1);
    assertThat(metrics.droppedSpans.get()).isZero();
  }

  @Test
  public void dropsWhenQueueIsFull() throws Exception {
    for (int i = 0; i < 1001; i++)
      collector.collect(span(1L, "foo"));

    collector.flush(); // manually flush the spans

    assertThat(zipkin.receivedSpanCount()).isEqualTo(1000);
    assertThat(metrics.droppedSpans.get()).isEqualTo(1);
  }

  @Test
  public void postsSpans() throws Exception {
    collector.collect(span(1L, "foo"));
    collector.collect(span(2L, "bar"));

    collector.flush(); // manually flush the spans

    // Ensure only one request was sent
    assertThat(zipkin.httpRequestCount()).isEqualTo(1);

    // Now, let's read back the spans we sent!
    assertThat(zipkin.getTraces()).containsExactly(
        asList(zipkinSpan(1L, "foo")),
        asList(zipkinSpan(2L, "bar"))
    );
  }

  @Test
  public void postsCompressedSpans() throws Exception {
    char[] annotation2K = new char[2048];
    Arrays.fill(annotation2K, 'a');

    ZipkinRule zipkin = new ZipkinRule();
    try {
      zipkin.start(0);

      HttpSpanCollector.Config config = HttpSpanCollector.Config.builder()
          .flushInterval(0).compressionEnabled(true).build();

      HttpSpanCollector collector = new HttpSpanCollector(zipkin.httpUrl(), config, metrics);

      collector.collect(span(1L, "foo")
          .addToAnnotations(Annotation.create(1111L, new String(annotation2K), null)));

      collector.flush(); // manually flush the span

      // Ensure the span was compressed
      assertThat(zipkin.receivedSpanBytes()).isLessThan(annotation2K.length);
    } finally {
      zipkin.shutdown();
    }
  }

  @Test
  public void incrementsDroppedSpansWhenServerErrors() throws Exception {
    zipkin.enqueueFailure(HttpFailure.sendErrorResponse(500, "Server Error!"));

    collector.collect(span(1L, "foo"));
    collector.collect(span(2L, "bar"));

    collector.flush(); // manually flush the spans

    assertThat(metrics.droppedSpans.get()).isEqualTo(2);
  }

  @Test
  public void incrementsDroppedSpansWhenServerDisconnects() throws Exception {
    zipkin.enqueueFailure(HttpFailure.disconnectDuringBody());

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
    return new zipkin.Span.Builder().traceId(traceId).id(traceId).name(spanName).build();
  }
}

package com.github.kristofa.brave.http;

import com.github.kristofa.brave.SpanCollectorMetricsHandler;
import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.Span;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import zipkin.junit.HttpFailure;
import zipkin.junit.ZipkinRule;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class HttpSpanCollectorTest {

  @Rule
  public final ZipkinRule zipkinRule = new ZipkinRule();

  TestMetricsHandler metrics = new TestMetricsHandler();
  // set flush interval to 0 so that tests can drive flushing explicitly
  HttpSpanCollector.Config config = HttpSpanCollector.Config.builder().flushInterval(0).build();
  HttpSpanCollector collector = new HttpSpanCollector(zipkinRule.httpUrl(), config, metrics);

  @After
  public void closeCollector(){
    collector.close();
  }

  @Test
  public void collectDoesntDoIO() throws Exception {
    collector.collect(span(1L, "foo"));

    assertThat(zipkinRule.httpRequestCount()).isZero();
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

    assertThat(zipkinRule.collectorMetrics().spans()).isEqualTo(1000);
    assertThat(metrics.droppedSpans.get()).isEqualTo(1);
  }

  @Test
  public void postsSpans() throws Exception {
    collector.collect(span(1L, "foo"));
    collector.collect(span(2L, "bar"));

    collector.flush(); // manually flush the spans

    // Ensure only one request was sent
    assertThat(zipkinRule.httpRequestCount()).isEqualTo(1);

    // Now, let's read back the spans we sent!
    assertThat(zipkinRule.getTraces()).containsExactly(
        asList(zipkinSpan(1L, "foo")),
        asList(zipkinSpan(2L, "bar"))
    );
  }

  @Test
  public void postsCompressedSpans() throws Exception {
    char[] annotation2K = new char[2048];
    Arrays.fill(annotation2K, 'a');

    MockWebServer zipkin = new MockWebServer();
    try {
      zipkin.start(0);
      zipkin.enqueue(new MockResponse());

      HttpSpanCollector.Config config = HttpSpanCollector.Config.builder()
          .flushInterval(0).compressionEnabled(true).build();

      HttpSpanCollector collector = new HttpSpanCollector(zipkin.url("/").toString(), config, metrics);

      collector.collect(span(1L, "foo")
          .addToAnnotations(Annotation.create(1111L, new String(annotation2K), null)));

      collector.flush(); // manually flush the span

      // Ensure the span was compressed
      assertThat(zipkin.takeRequest().getBodySize())
          .isLessThan(annotation2K.length);
    } finally {
      zipkin.shutdown();
    }
  }

  @Test
  public void incrementsDroppedSpansWhenServerErrors() throws Exception {
    zipkinRule.enqueueFailure(HttpFailure.sendErrorResponse(500, "Server Error!"));

    collector.collect(span(1L, "foo"));
    collector.collect(span(2L, "bar"));

    collector.flush(); // manually flush the spans

    assertThat(metrics.droppedSpans.get()).isEqualTo(2);
  }

  @Test
  public void incrementsDroppedSpansWhenServerDisconnects() throws Exception {
    zipkinRule.enqueueFailure(HttpFailure.disconnectDuringBody());

    collector.collect(span(1L, "foo"));
    collector.collect(span(2L, "bar"));

    collector.flush(); // manually flush the spans

    assertThat(metrics.droppedSpans.get()).isEqualTo(2);
  }

  static class TestMetricsHandler implements SpanCollectorMetricsHandler {

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
}

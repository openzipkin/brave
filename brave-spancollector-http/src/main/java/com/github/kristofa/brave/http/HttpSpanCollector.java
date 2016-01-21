package com.github.kristofa.brave.http;

import com.github.kristofa.brave.*;
import com.github.kristofa.brave.internal.Nullable;
import com.google.auto.value.AutoValue;
import com.twitter.zipkin.gen.Span;
import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TList;
import org.apache.thrift.protocol.TType;
import org.apache.thrift.transport.TMemoryBuffer;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * SpanCollector which submits spans to Zipkin, using its {@code POST /spans} endpoint.
 */
public final class HttpSpanCollector implements SpanCollector, Flushable, Closeable {

  @AutoValue
  public static abstract class Config {
    public static Builder builder() {
      return new AutoValue_HttpSpanCollector_Config.Builder()
          .connectTimeout(10 * 1000)
          .readTimeout(60 * 1000)
          .flushInterval(1);
    }

    abstract int connectTimeout();

    abstract int readTimeout();

    abstract int flushInterval();

    @AutoValue.Builder
    public interface Builder {
      /** Default 10 * 1000 milliseconds. 0 implies no timeout. */
      Builder connectTimeout(int connectTimeout);

      /** Default 60 * 1000 milliseconds. 0 implies no timeout. */
      Builder readTimeout(int readTimeout);

      /** Default 1 second. 0 implies spans are {@link #flush() flushed} externally. */
      Builder flushInterval(int flushInterval);

      Config build();
    }
  }

  private final String url;
  private final Config config;
  private final SpanCollectorMetricsHandler metrics;
  private final BlockingQueue<Span> pending = new LinkedBlockingQueue<>(1000);
  @Nullable // for testing
  private final Flusher flusher;

  /**
   * Create a new instance with default configuration.
   *
   * @param baseUrl URL of the zipkin query server instance. Like: http://localhost:9411/
   * @param metrics Gets notified when spans are accepted or dropped. If you are not interested in
   * these events you can use {@linkplain EmptySpanCollectorMetricsHandler}
   */
  public static HttpSpanCollector create(String baseUrl, SpanCollectorMetricsHandler metrics) {
    return new HttpSpanCollector(baseUrl, Config.builder().build(), metrics);
  }

  /**
   * @param baseUrl URL of the zipkin query server instance. Like: http://localhost:9411/
   * @param config controls flush interval and timeouts
   * @param metrics Gets notified when spans are accepted or dropped. If you are not interested in
   * these events you can use {@linkplain EmptySpanCollectorMetricsHandler}
   */
  public static HttpSpanCollector create(String baseUrl, Config config,
      SpanCollectorMetricsHandler metrics) {
    return new HttpSpanCollector(baseUrl, config, metrics);
  }

  // Visible for testing. Ex when tests need to explicitly control flushing, set interval to 0.
  HttpSpanCollector(String baseUrl, Config config,
      SpanCollectorMetricsHandler metrics) {
    this.url = baseUrl + (baseUrl.endsWith("/") ? "" : "/") + "api/v1/spans";
    this.metrics = metrics;
    this.config = config;
    this.flusher = config.flushInterval() > 0 ? new Flusher(this, config.flushInterval()) : null;
  }

  /**
   * Queues the span for collection, or drops it if the queue is full.
   *
   * @param span Span, should not be <code>null</code>.
   */
  @Override
  public void collect(Span span) {
    metrics.incrementAcceptedSpans(1);
    if (!pending.offer(span)) {
      metrics.incrementDroppedSpans(1);
    }
  }

  /**
   * Calling this will flush any pending spans to the http transport on the current thread.
   */
  @Override
  public void flush() {
    if (pending.isEmpty()) return;
    Queue<Span> drained = new ArrayDeque<>(pending.size());
    pending.drainTo(drained);
    if (drained.isEmpty()) return;

    // Thrift-encode the spans for transport
    int spanCount = drained.size();
    TMemoryBuffer thrifts = new TMemoryBuffer(spanCount * 200); // guess 200bytes/span
    try {
      TBinaryProtocol oprot = new TBinaryProtocol(thrifts);
      oprot.writeListBegin(new TList(TType.STRUCT, drained.size()));
      while (!drained.isEmpty()) {
        drained.remove().write(oprot);
      }
      oprot.writeListEnd();
    } catch (TException e) {
      metrics.incrementDroppedSpans(spanCount);
      return;
    }

    // Send the thrifts to the zipkin endpoint
    try {
      postSpans(thrifts.getArray());
    } catch (IOException e) {
      metrics.incrementDroppedSpans(spanCount);
      return;
    }
  }

  /** Calls flush on a fixed interval */
  static final class Flusher implements Runnable {
    final Flushable flushable;
    final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    Flusher(Flushable flushable, int flushInterval) {
      this.flushable = flushable;
      this.scheduler.scheduleWithFixedDelay(this, 0, flushInterval, SECONDS);
    }

    @Override
    public void run() {
      try {
        flushable.flush();
      } catch (IOException ignored) {
      }
    }
  }

  void postSpans(byte[] thrifts) throws IOException {
    // intentionally not closing the connection, so as to use keep-alives
    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    connection.setConnectTimeout(config.connectTimeout());
    connection.setReadTimeout(config.readTimeout());
    connection.setRequestMethod("POST");
    connection.addRequestProperty("Content-Type", "application/x-thrift");
    connection.setDoOutput(true);
    connection.setFixedLengthStreamingMode(thrifts.length);
    connection.getOutputStream().write(thrifts);

    try (InputStream in = connection.getInputStream()) {
      while (in.read() != -1) ; // skip
    } catch (IOException e) {
      try (InputStream err = connection.getErrorStream()) {
        if (err != null) { // possible, if the connection was dropped
          while (err.read() != -1) ; // skip
        }
      }
      throw e;
    }
  }

  @Override
  public void addDefaultAnnotation(String key, String value) {
    throw new UnsupportedOperationException();
  }

  /**
   * Requests a cease of delivery. There will be at most one in-flight request processing after this
   * call returns.
   */
  @Override
  public void close() {
    if (flusher != null) flusher.scheduler.shutdown();
    // throw any outstanding spans on the floor
    int dropped = pending.drainTo(new LinkedList<>());
    metrics.incrementDroppedSpans(dropped);
  }
}

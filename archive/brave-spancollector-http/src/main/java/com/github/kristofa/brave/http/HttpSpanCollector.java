package com.github.kristofa.brave.http;

import com.github.kristofa.brave.AbstractSpanCollector;
import com.github.kristofa.brave.EmptySpanCollectorMetricsHandler;
import com.github.kristofa.brave.SpanCollectorMetricsHandler;
import com.google.auto.value.AutoValue;
import com.twitter.zipkin.gen.SpanCodec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.GZIPOutputStream;

/**
 * SpanCollector which submits spans to Zipkin, using its {@code POST /spans} endpoint.
 *
 * @deprecated replaced by {@link zipkin.reporter.AsyncReporter} and {@code URLConnectionSender}
 *             located in the "io.zipkin.reporter:zipkin-sender-urlconnection" dependency.
 */
@Deprecated
public final class HttpSpanCollector extends AbstractSpanCollector {

  @AutoValue
  public static abstract class Config {
    public static Builder builder() {
      return new AutoValue_HttpSpanCollector_Config.Builder()
          .connectTimeout(10 * 1000)
          .readTimeout(60 * 1000)
          .compressionEnabled(false)
          .flushInterval(1);
    }

    abstract int connectTimeout();

    abstract int readTimeout();

    abstract int flushInterval();

    abstract boolean compressionEnabled();

    @AutoValue.Builder
    public interface Builder {
      /** Default 10 * 1000 milliseconds. 0 implies no timeout. */
      Builder connectTimeout(int connectTimeout);

      /** Default 60 * 1000 milliseconds. 0 implies no timeout. */
      Builder readTimeout(int readTimeout);

      /** Default 1 second. 0 implies spans are {@link #flush() flushed} externally. */
      Builder flushInterval(int flushInterval);

      /**
       * Default false. true implies that spans will be gzipped before transport.
       *
       * <p>Note: This feature requires zipkin-scala 1.34+ or zipkin-java 0.6+
       */
      Builder compressionEnabled(boolean compressSpans);

      Config build();
    }
  }

  private final String url;
  private final Config config;

  /**
   * Create a new instance with default configuration.
   *
   * @param baseUrl URL of the zipkin query server instance. Like: http://localhost:9411/
   * @param metrics Gets notified when spans are accepted or dropped. If you are not interested in
   *                these events you can use {@linkplain EmptySpanCollectorMetricsHandler}
   */
  public static HttpSpanCollector create(String baseUrl, SpanCollectorMetricsHandler metrics) {
    return new HttpSpanCollector(baseUrl, Config.builder().build(), metrics);
  }

  /**
   * @param baseUrl URL of the zipkin query server instance. Like: http://localhost:9411/
   * @param config includes flush interval and timeouts
   * @param metrics Gets notified when spans are accepted or dropped. If you are not interested in
   *                these events you can use {@linkplain EmptySpanCollectorMetricsHandler}
   */
  public static HttpSpanCollector create(String baseUrl, Config config,
      SpanCollectorMetricsHandler metrics) {
    return new HttpSpanCollector(baseUrl, config, metrics);
  }

  // Visible for testing. Ex when tests need to explicitly control flushing, set interval to 0.
  HttpSpanCollector(String baseUrl, Config config, SpanCollectorMetricsHandler metrics) {
    super(SpanCodec.JSON, metrics,  config.flushInterval());
    this.url = baseUrl + (baseUrl.endsWith("/") ? "" : "/") + "api/v1/spans";
    this.config = config;
  }

  @Override
  protected void sendSpans(byte[] json) throws IOException {
    // intentionally not closing the connection, so as to use keep-alives
    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    connection.setConnectTimeout(config.connectTimeout());
    connection.setReadTimeout(config.readTimeout());
    connection.setRequestMethod("POST");
    connection.addRequestProperty("Content-Type", "application/json");
    if (config.compressionEnabled()) {
      connection.addRequestProperty("Content-Encoding", "gzip");
      ByteArrayOutputStream gzipped = new ByteArrayOutputStream();
      try (GZIPOutputStream compressor = new GZIPOutputStream(gzipped)) {
        compressor.write(json);
      }
      json = gzipped.toByteArray();
    }
    connection.setDoOutput(true);
    connection.setFixedLengthStreamingMode(json.length);
    connection.getOutputStream().write(json);

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
}

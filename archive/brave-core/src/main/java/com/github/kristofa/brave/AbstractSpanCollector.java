package com.github.kristofa.brave;

import com.twitter.zipkin.gen.Span;
import com.twitter.zipkin.gen.SpanCodec;
import java.io.IOException;
import java.util.List;

/**
 * Implemented {@link #sendSpans} to transport a encoded list of spans to Zipkin.
 *
 * @deprecated replaced by {@link zipkin2.reporter.AsyncReporter}
 */
@Deprecated
public abstract class AbstractSpanCollector extends FlushingSpanCollector {

  private final SpanCodec codec;

  /**
   * @param flushInterval in seconds. 0 implies spans are {@link #flush() flushed externally.
   */
  public AbstractSpanCollector(SpanCodec codec, SpanCollectorMetricsHandler metrics,
      int flushInterval) {
    super(metrics, flushInterval);
    this.codec = codec;
  }

  @Override
  protected void reportSpans(List<Span> drained) throws IOException {
    byte[] encoded = codec.writeSpans(drained);
    sendSpans(encoded);
  }

  /**
   * Sends a encoded list of spans over the current transport.
   *
   * @throws IOException when thrown, drop metrics will increment accordingly
   */
  protected abstract void sendSpans(byte[] encoded) throws IOException;
}

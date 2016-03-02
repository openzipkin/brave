package com.github.kristofa.brave;

import com.github.kristofa.brave.internal.Nullable;
import com.twitter.zipkin.gen.Span;
import com.twitter.zipkin.gen.SpanCodec;
import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Implemented {@link #sendSpans} to transport a encoded list of spans to Zipkin.
 */
public abstract class AbstractSpanCollector implements SpanCollector, Flushable, Closeable {

  private final SpanCodec codec;
  private final SpanCollectorMetricsHandler metrics;
  private final BlockingQueue<Span> pending = new LinkedBlockingQueue<Span>(1000);
  @Nullable // for testing
  private final Flusher flusher;

  /**
   * @param flushInterval in seconds. 0 implies spans are {@link #flush() flushed externally.
   */
  public AbstractSpanCollector(SpanCodec codec, SpanCollectorMetricsHandler metrics,
      int flushInterval) {
    this.codec = codec;
    this.metrics = metrics;
    this.flusher = flushInterval > 0 ? new Flusher(this, flushInterval) : null;
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
   * Calling this will flush any pending spans to the transport on the current thread.
   */
  @Override
  public void flush() {
    if (pending.isEmpty()) return;
    List<Span> drained = new ArrayList<Span>(pending.size());
    pending.drainTo(drained);
    if (drained.isEmpty()) return;

    // encode the spans for transport
    int spanCount = drained.size();
    byte[] encoded;
    try {
      encoded = codec.writeSpans(drained);
    } catch (RuntimeException e) {
      metrics.incrementDroppedSpans(spanCount);
      return;
    }

    // transport the spans
    try {
      sendSpans(encoded);
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

  /**
   * Sends a encoded list of spans over the current transport.
   *
   * @throws IOException when thrown, drop metrics will increment accordingly
   */
  protected abstract void sendSpans(byte[] encoded) throws IOException;

  @Override
  public void addDefaultAnnotation(String key, String value) {
    throw new UnsupportedOperationException();
  }

  /**
   * Requests a cease of delivery. There will be at most one in-flight send after this call.
   */
  @Override
  public void close() {
    if (flusher != null) flusher.scheduler.shutdown();
    // throw any outstanding spans on the floor
    int dropped = pending.drainTo(new LinkedList<Span>());
    metrics.incrementDroppedSpans(dropped);
  }
}

package com.github.kristofa.brave;

import com.github.kristofa.brave.internal.Nullable;
import com.twitter.zipkin.gen.Span;
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
import java.util.concurrent.ThreadFactory;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Extend this class to offload the task of reporting spans to separate thread. By doing so, callers
 * are protected from latency or exceptions possible when exporting spans out of process.
 *
 * @deprecated replaced by {@link zipkin.reporter.AsyncReporter}
 */
@Deprecated
public abstract class FlushingSpanCollector implements SpanCollector, Flushable, Closeable {

  private final SpanCollectorMetricsHandler metrics;
  private final BlockingQueue<Span> pending = new LinkedBlockingQueue<Span>(1000);
  @Nullable // for testing
  private final Flusher flusher;

  /**
   * @param flushInterval in seconds. 0 implies spans are {@link #flush() flushed externally.
   */
  protected FlushingSpanCollector(SpanCollectorMetricsHandler metrics, int flushInterval) {
    this.metrics = metrics;
    this.flusher = flushInterval > 0 ? new Flusher(this, flushInterval, getClass().getSimpleName()) : null;
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

    int spanCount = drained.size();
    try {
      reportSpans(drained);
    } catch (IOException e) {
      metrics.incrementDroppedSpans(spanCount);
    } catch (RuntimeException e) {
      metrics.incrementDroppedSpans(spanCount);
    }
  }

  /** Calls flush on a fixed interval */
  static final class Flusher implements Runnable {
    final Flushable flushable;
    final ScheduledExecutorService scheduler;

    Flusher(Flushable flushable, int flushInterval, final String threadPoolName) {
      this.flushable = flushable;
      this.scheduler = Executors.newSingleThreadScheduledExecutor(
          r -> new Thread(r, threadPoolName));
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
   * Reports a list of spans over the current transport.
   *
   * @throws IOException (or RuntimeException) when thrown, drop metrics will increment accordingly
   */
  protected abstract void reportSpans(List<Span> drained) throws IOException;

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

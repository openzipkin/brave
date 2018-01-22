package brave.internal.recorder;

import brave.Clock;
import brave.Span;
import brave.internal.Nullable;
import brave.propagation.TraceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import zipkin2.Endpoint;
import zipkin2.reporter.Reporter;

/** Dispatches mutations on a span to a shared object per trace/span id. */
public final class Recorder {
  final MutableSpanMap spanMap;
  final Clock clock;
  final Reporter<zipkin2.Span> reporter;
  final AtomicBoolean noop;

  public Recorder(
      Endpoint localEndpoint,
      Clock clock,
      Reporter<zipkin2.Span> reporter,
      AtomicBoolean noop
  ) {
    this.spanMap = new MutableSpanMap(localEndpoint, clock, reporter, noop);
    this.clock = clock;
    this.reporter = reporter;
    this.noop = noop;
  }

  /**
   * Hook needed for Brave 3's LocalTracer.finish(duration)
   *
   * @see Span#start()
   */
  @Nullable public Long timestamp(TraceContext context) {
    MutableSpan span = spanMap.get(context);
    if (span == null) return null;
    return span.timestamp == 0 ? null : span.timestamp;
  }

  /** Returns a clock that ensures timestamp consistency across the trace */
  public Clock clock(TraceContext context) {
    if (noop.get()) return clock;
    return spanMap.getOrCreate(context).clock;
  }

  /** @see brave.Span#start() */
  public void start(TraceContext context) {
    if (noop.get()) return;
    spanMap.getOrCreate(context).start();
  }

  /** @see brave.Span#start(long) */
  public void start(TraceContext context, long timestamp) {
    if (noop.get()) return;
    spanMap.getOrCreate(context).start(timestamp);
  }

  /** @see brave.Span#name(String) */
  public void name(TraceContext context, String name) {
    if (noop.get()) return;
    if (name == null) throw new NullPointerException("name == null");
    spanMap.getOrCreate(context).name(name);
  }

  /** @see brave.Span#kind(Span.Kind) */
  public void kind(TraceContext context, Span.Kind kind) {
    if (noop.get()) return;
    if (kind == null) throw new NullPointerException("kind == null");
    spanMap.getOrCreate(context).kind(kind);
  }

  /** @see brave.Span#annotate(String) */
  public void annotate(TraceContext context, String value) {
    if (noop.get()) return;
    if (value == null) throw new NullPointerException("value == null");
    spanMap.getOrCreate(context).annotate(value);
  }

  /** @see brave.Span#annotate(long, String) */
  public void annotate(TraceContext context, long timestamp, String value) {
    if (noop.get()) return;
    if (value == null) throw new NullPointerException("value == null");
    spanMap.getOrCreate(context).annotate(timestamp, value);
  }

  /** @see brave.Span#tag(String, String) */
  public void tag(TraceContext context, String key, String value) {
    if (noop.get()) return;
    if (key == null) throw new NullPointerException("key == null");
    if (key.isEmpty()) throw new IllegalArgumentException("key is empty");
    if (value == null) throw new NullPointerException("value == null");
    spanMap.getOrCreate(context).tag(key, value);
  }

  /** @see brave.Span#remoteEndpoint(Endpoint) */
  public void remoteEndpoint(TraceContext context, Endpoint remoteEndpoint) {
    if (noop.get()) return;
    if (remoteEndpoint == null) throw new NullPointerException("remoteEndpoint == null");
    spanMap.getOrCreate(context).remoteEndpoint(remoteEndpoint);
  }

  /** @see Span#finish() */
  public void finish(TraceContext context) {
    MutableSpan span = spanMap.remove(context);
    if (span == null || noop.get()) return;
    synchronized (span) {
      span.finish(span.clock.currentTimeMicroseconds());
      reporter.report(span.toSpan());
    }
  }

  /** @see Span#finish(long) */
  public void finish(TraceContext context, long finishTimestamp) {
    MutableSpan span = spanMap.remove(context);
    if (span == null || noop.get()) return;
    synchronized (span) {
      span.finish(finishTimestamp);
      reporter.report(span.toSpan());
    }
  }

  /** @see Span#abandon() */
  public void abandon(TraceContext context) {
    spanMap.remove(context);
  }

  /** @see Span#flush() */
  public void flush(TraceContext context) {
    MutableSpan span = spanMap.remove(context);
    if (span == null || noop.get()) return;
    synchronized (span) {
      span.finish(null);
      reporter.report(span.toSpan());
    }
  }

  /** Exposes which spans are in-flight, mostly for testing. */
  public List<zipkin2.Span> snapshot() {
    List<zipkin2.Span> result = new ArrayList<>();
    for (MutableSpan value : spanMap.delegate.values()) {
      result.add(value.toSpan());
    }
    return result;
  }
}

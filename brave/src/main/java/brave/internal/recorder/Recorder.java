package brave.internal.recorder;

import brave.Clock;
import brave.Span;
import brave.propagation.TraceContext;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import zipkin.Endpoint;
import zipkin.reporter.Reporter;

/** Dispatches mutations on a span to a shared object per trace/span id. */
public final class Recorder {

  final MutableSpanMap spanMap;
  final Reporter<zipkin.Span> reporter;
  final AtomicBoolean noop;

  public Recorder(
      Endpoint localEndpoint,
      Clock clock,
      Reporter<zipkin.Span> reporter,
      AtomicBoolean noop
  ) {
    this.spanMap = new MutableSpanMap(localEndpoint, clock, reporter, noop);
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
    return span.startTimestamp == 0 ? null : span.startTimestamp;
  }

  /** @see brave.Span#start(long) */
  public void start(TraceContext context, long timestamp) {
    spanMap.getOrCreate(context).start(timestamp);
  }

  /** @see brave.Span#name(String) */
  public void name(TraceContext context, String name) {
    if (name == null) throw new NullPointerException("name == null");
    spanMap.getOrCreate(context).name(name);
  }

  /** @see brave.Span#kind(Span.Kind) */
  public void kind(TraceContext context, Span.Kind kind) {
    if (kind == null) throw new NullPointerException("kind == null");
    spanMap.getOrCreate(context).kind(kind);
  }

  /** @see brave.Span#annotate(long, String) */
  public void annotate(TraceContext context, long timestamp, String value) {
    if (value == null) throw new NullPointerException("value == null");
    spanMap.getOrCreate(context).annotate(timestamp, value);
  }

  /** @see brave.Span#tag(String, String) */
  public void tag(TraceContext context, String key, String value) {
    if (key == null) throw new NullPointerException("key == null");
    if (key.isEmpty()) throw new IllegalArgumentException("key is empty");
    if (value == null) throw new NullPointerException("value == null");
    spanMap.getOrCreate(context).tag(key, value);
  }

  /** @see brave.Span#remoteEndpoint(Endpoint) */
  public void remoteEndpoint(TraceContext context, Endpoint remoteEndpoint) {
    if (remoteEndpoint == null) throw new NullPointerException("remoteEndpoint == null");
    spanMap.getOrCreate(context).remoteEndpoint(remoteEndpoint);
  }

  /** @see Span#finish() */
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
}

package brave.internal.recorder;

import brave.Clock;
import brave.propagation.TraceContext;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import zipkin.Endpoint;
import zipkin.reporter.Reporter;

/**
 * Similar to Finagle's deadline span map, except this is GC pressure as opposed to timeout driven.
 * This means there's no bookkeeping thread required in order to flush orphaned spans.
 *
 * <p>Spans are weakly referenced by their owning context. When the keys are collected, they are
 * transferred to a queue, waiting to be reported. A call to modify any span will implicitly flush
 * orphans to Zipkin. Spans in this state will have a "brave.flush" annotation added to them.
 *
 * <p>The internal implementation is derived from WeakConcurrentMap by Rafael Winterhalter. See
 * https://github.com/raphw/weak-lock-free/blob/master/src/main/java/com/blogspot/mydailyjava/weaklockfree/WeakConcurrentMap.java
 */
final class MutableSpanMap extends ReferenceQueue<TraceContext> {
  static final Logger logger = Logger.getLogger(MutableSpanMap.class.getName());

  // Eventhough we only put by RealKey, we allow get and remove by LookupKey
  final ConcurrentMap<Object, MutableSpan> delegate = new ConcurrentHashMap<>(64);
  final Endpoint localEndpoint;
  final Clock clock;
  final Reporter<zipkin.Span> reporter;
  final AtomicBoolean noop;

  MutableSpanMap(
      Endpoint localEndpoint,
      Clock clock,
      Reporter<zipkin.Span> reporter,
      AtomicBoolean noop
  ) {
    this.localEndpoint = localEndpoint;
    this.clock = clock;
    this.reporter = reporter;
    this.noop = noop;
  }

  @Nullable MutableSpan get(TraceContext context) {
    if (context == null) throw new NullPointerException("context == null");
    reportOrphanedSpans();
    return delegate.get(new LookupKey(context));
  }

  MutableSpan getOrCreate(TraceContext context) {
    MutableSpan result = get(context);
    if (result != null) return result;

    MutableSpan newSpan = new MutableSpan(context, localEndpoint);
    MutableSpan previousSpan = delegate.putIfAbsent(new RealKey(context, this), newSpan);
    if (previousSpan != null) return previousSpan; // lost race
    return newSpan;
  }

  @Nullable MutableSpan remove(TraceContext context) {
    if (context == null) throw new NullPointerException("context == null");
    MutableSpan result = delegate.remove(new LookupKey(context));
    reportOrphanedSpans(); // also clears the reference relating to the recent remove
    return result;
  }

  /** Reports spans orphaned by garbage collection. */
  void reportOrphanedSpans() {
    Reference<? extends TraceContext> reference;
    while ((reference = poll()) != null) {
      TraceContext context = reference.get();
      MutableSpan value = delegate.remove(reference);
      if (value == null || noop.get()) continue;
      try {
        value.annotate(clock.currentTimeMicroseconds(), "brave.flush");
        reporter.report(value.toSpan());
      } catch (RuntimeException e) {
        // don't crash the caller if there was a problem reporting an unrelated span.
        if (context != null && logger.isLoggable(Level.FINE)) {
          logger.log(Level.FINE, "error flushing " + context, e);
        }
      }
    }
  }

  /**
   * Real keys contain a reference to the real context associated with a span. This is a weak
   * reference, so that we get notified on GC pressure.
   *
   * <p>Since {@linkplain TraceContext}'s hash code is final, it is used directly both here and in
   * lookup keys.
   */
  static final class RealKey extends WeakReference<TraceContext> {
    final int hashCode;

    RealKey(TraceContext context, ReferenceQueue<TraceContext> queue) {
      super(context, queue);
      hashCode = context.hashCode();
    }

    @Override public String toString() {
      TraceContext context = get();
      return context != null ? "WeakReference(" + context + ")" : "ClearedReference()";
    }

    @Override public int hashCode() {
      return this.hashCode;
    }

    /** Resolves hash code collisions */
    @Override public boolean equals(Object other) {
      TraceContext thisContext = get(), thatContext = ((RealKey) other).get();
      if (thisContext == null) {
        return thatContext == null;
      } else {
        return thisContext.equals(thatContext);
      }
    }
  }

  /**
   * Lookup keys are cheaper than real keys as reference tracking is not involved. We cannot use
   * {@linkplain TraceContext} directly as a lookup key, as eventhough it has the same hash code as
   * the real key, it would fail in equals comparison.
   */
  static final class LookupKey {
    final TraceContext context;

    LookupKey(TraceContext context) {
      this.context = context;
    }

    @Override public int hashCode() {
      return context.hashCode();
    }

    /** Resolves hash code collisions */
    @Override public boolean equals(Object other) {
      return context.equals(((RealKey) other).get());
    }
  }

  @Override public String toString() {
    return "MutableSpanMap" + delegate.keySet();
  }
}

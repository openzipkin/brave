package brave.context.log4j2;

import brave.internal.HexCodec;
import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import org.apache.logging.log4j.ThreadContext;

import static brave.internal.HexCodec.lowerHexEqualsTraceId;
import static brave.internal.HexCodec.lowerHexEqualsUnsignedLong;

/**
 * Adds {@linkplain ThreadContext} properties "traceId", "parentId" and "spanId" when a {@link
 * brave.Tracer#currentSpan() span is current}. These can be used in log correlation.
 */
public final class ThreadContextCurrentTraceContext extends CurrentTraceContext {
  public static ThreadContextCurrentTraceContext create() {
    return create(CurrentTraceContext.Default.inheritable());
  }

  public static ThreadContextCurrentTraceContext create(CurrentTraceContext delegate) {
    return new ThreadContextCurrentTraceContext(delegate);
  }

  final CurrentTraceContext delegate;

  ThreadContextCurrentTraceContext(CurrentTraceContext delegate) {
    if (delegate == null) throw new NullPointerException("delegate == null");
    this.delegate = delegate;
  }

  @Override public TraceContext get() {
    return delegate.get();
  }

  @Override public Scope newScope(@Nullable TraceContext currentSpan) {
    return newScope(currentSpan, ThreadContext.get("traceId"), ThreadContext.get("spanId"));
  }

  @Override public Scope maybeScope(@Nullable TraceContext currentSpan) {
    String previousTraceId = ThreadContext.get("traceId");
    String previousSpanId = ThreadContext.get("spanId");
    if (currentSpan == null) {
      if (previousTraceId == null) return Scope.NOOP;
      return newScope(null, previousTraceId, previousSpanId);
    }
    if (lowerHexEqualsTraceId(previousTraceId, currentSpan)
        && lowerHexEqualsUnsignedLong(previousSpanId, currentSpan.spanId())) {
      return Scope.NOOP;
    }
    return newScope(currentSpan, previousTraceId, previousSpanId);
  }

  // all input parameters are nullable
  Scope newScope(TraceContext currentSpan, String previousTraceId, String previousSpanId) {
    String previousParentId = ThreadContext.get("parentId");
    if (currentSpan != null) {
      maybeReplaceTraceContext(currentSpan, previousTraceId, previousParentId, previousSpanId);
    } else {
      ThreadContext.remove("traceId");
      ThreadContext.remove("parentId");
      ThreadContext.remove("spanId");
    }

    Scope scope = delegate.newScope(currentSpan);
    class ThreadContextCurrentTraceContextScope implements Scope {
      @Override public void close() {
        scope.close();
        replace("traceId", previousTraceId);
        replace("parentId", previousParentId);
        replace("spanId", previousSpanId);
      }
    }
    return new ThreadContextCurrentTraceContextScope();
  }

  void maybeReplaceTraceContext(
      TraceContext currentSpan,
      String previousTraceId,
      @Nullable String previousParentId,
      String previousSpanId
  ) {
    boolean sameTraceId = lowerHexEqualsTraceId(previousTraceId, currentSpan);
    if (!sameTraceId) ThreadContext.put("traceId", currentSpan.traceIdString());

    long parentId = currentSpan.parentIdAsLong();
    if (parentId == 0L) {
      ThreadContext.remove("parentId");
    } else {
      boolean sameParentId = lowerHexEqualsUnsignedLong(previousParentId, parentId);
      if (!sameParentId) ThreadContext.put("parentId", HexCodec.toLowerHex(parentId));
    }

    boolean sameSpanId = lowerHexEqualsUnsignedLong(previousSpanId, currentSpan.spanId());
    if (!sameSpanId) ThreadContext.put("spanId", HexCodec.toLowerHex(currentSpan.spanId()));
  }

  static void replace(String key, @Nullable String value) {
    if (value != null) {
      ThreadContext.put(key, value);
    } else {
      ThreadContext.remove(key);
    }
  }
}

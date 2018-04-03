package brave.context.log4j12;

import brave.internal.HexCodec;
import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import org.apache.log4j.MDC;

import static brave.internal.HexCodec.lowerHexEqualsTraceId;
import static brave.internal.HexCodec.lowerHexEqualsUnsignedLong;

/**
 * Adds {@linkplain MDC} properties "traceId", "parentId" and "spanId" when a {@link brave.Tracer#currentSpan()
 * span is current}. These can be used in log correlation.
 */
public final class MDCCurrentTraceContext extends CurrentTraceContext {
  public static MDCCurrentTraceContext create() {
    return create(CurrentTraceContext.Default.inheritable());
  }

  public static MDCCurrentTraceContext create(CurrentTraceContext delegate) {
    return new MDCCurrentTraceContext(delegate);
  }

  final CurrentTraceContext delegate;

  MDCCurrentTraceContext(CurrentTraceContext delegate) {
    if (delegate == null) {
      throw new NullPointerException("delegate == null");
    }
    this.delegate = delegate;
  }

  @Override public TraceContext get() {
    return delegate.get();
  }

  @Override public Scope newScope(@Nullable TraceContext currentSpan) {
    return newScope(currentSpan, getIfString("traceId"), getIfString("spanId"));
  }

  @Override public Scope maybeScope(@Nullable TraceContext currentSpan) {
    String previousTraceId = getIfString("traceId");
    String previousSpanId = getIfString("spanId");
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
    String previousParentId = getIfString("parentId");
    if (currentSpan != null) {
      maybeReplaceTraceContext(currentSpan, previousTraceId, previousParentId, previousSpanId);
    } else {
      MDC.remove("traceId");
      MDC.remove("parentId");
      MDC.remove("spanId");
    }

    Scope scope = delegate.newScope(currentSpan);
    class MDCCurrentTraceContextScope implements Scope {
      @Override public void close() {
        scope.close();
        replace("traceId", previousTraceId);
        replace("parentId", previousParentId);
        replace("spanId", previousSpanId);
      }
    }
    return new MDCCurrentTraceContextScope();
  }

  void maybeReplaceTraceContext(
      TraceContext currentSpan,
      String previousTraceId,
      @Nullable String previousParentId,
      String previousSpanId
  ) {
    boolean sameTraceId = lowerHexEqualsTraceId(previousTraceId, currentSpan);
    if (!sameTraceId) MDC.put("traceId", currentSpan.traceIdString());

    long parentId = currentSpan.parentIdAsLong();
    if (parentId == 0L) {
      MDC.remove("parentId");
    } else {
      boolean sameParentId = lowerHexEqualsUnsignedLong(previousParentId, parentId);
      if (!sameParentId) MDC.put("parentId", HexCodec.toLowerHex(parentId));
    }

    boolean sameSpanId = lowerHexEqualsUnsignedLong(previousSpanId, currentSpan.spanId());
    if (!sameSpanId) MDC.put("spanId", HexCodec.toLowerHex(currentSpan.spanId()));
  }

  static String getIfString(String key) {
    Object result = MDC.get(key);
    return result instanceof String ? (String) result : null;
  }

  static void replace(String key, @Nullable Object value) {
    if (value != null) {
      MDC.put(key, value);
    } else {
      MDC.remove(key);
    }
  }
}

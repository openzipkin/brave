package brave.internal.propagation;

import brave.internal.HexCodec;
import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.CurrentTraceContext.ScopeDecorator;
import brave.propagation.TraceContext;

import static brave.internal.HexCodec.lowerHexEqualsTraceId;
import static brave.internal.HexCodec.lowerHexEqualsUnsignedLong;

/**
 * Adds correlation properties "traceId", "parentId" and "spanId" when a {@link
 * brave.Tracer#currentSpan() span is current}.
 */
public abstract class CorrelationFieldScopeDecorator implements ScopeDecorator {

  /**
   * When the input is not null "traceId", "parentId" and "spanId" correlation properties are saved
   * off and replaced with those of the current span. When the input is null, these properties are
   * removed. Either way, "traceId", "parentId" and "spanId" properties are restored on {@linkplain
   * Scope#close()}.
   */
  @Override public Scope decorateScope(@Nullable TraceContext currentSpan, Scope scope) {
    String previousTraceId = get("traceId");
    String previousSpanId = get("spanId");
    String previousParentId = get("parentId");

    if (currentSpan != null) {
      maybeReplaceTraceContext(currentSpan, previousTraceId, previousParentId, previousSpanId);
    } else {
      remove("traceId");
      remove("parentId");
      remove("spanId");
    }

    class CorrelationFieldCurrentTraceContextScope implements Scope {
      @Override public void close() {
        scope.close();
        replace("traceId", previousTraceId);
        replace("parentId", previousParentId);
        replace("spanId", previousSpanId);
      }
    }
    return new CorrelationFieldCurrentTraceContextScope();
  }

  /**
   * Idempotently sets correlation properties to hex representation of trace identifiers in this
   * context.
   */
  void maybeReplaceTraceContext(
      TraceContext currentSpan,
      String previousTraceId,
      @Nullable String previousParentId,
      String previousSpanId
  ) {
    boolean sameTraceId = lowerHexEqualsTraceId(previousTraceId, currentSpan);
    if (!sameTraceId) put("traceId", currentSpan.traceIdString());

    long parentId = currentSpan.parentIdAsLong();
    if (parentId == 0L) {
      remove("parentId");
    } else {
      boolean sameParentId = lowerHexEqualsUnsignedLong(previousParentId, parentId);
      if (!sameParentId) put("parentId", HexCodec.toLowerHex(parentId));
    }

    boolean sameSpanId = lowerHexEqualsUnsignedLong(previousSpanId, currentSpan.spanId());
    if (!sameSpanId) put("spanId", HexCodec.toLowerHex(currentSpan.spanId()));
  }

  /**
   * Returns the correlation property of the specified name iff it is a string, or null otherwise.
   */
  protected abstract @Nullable String get(String key);

  /** Replaces the correlation property of the specified name */
  protected abstract void put(String key, String value);

  /** Removes the correlation property of the specified name */
  protected abstract void remove(String key);

  final void replace(String key, @Nullable String value) {
    if (value != null) {
      put(key, value);
    } else {
      remove(key);
    }
  }
}

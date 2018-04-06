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

  @Override public Scope decorateScope(@Nullable TraceContext currentSpan, Scope scope) {
    String previousTraceId = getIfString("traceId");
    String previousSpanId = getIfString("spanId");
    String previousParentId = getIfString("parentId");

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

  protected abstract @Nullable String getIfString(String key);

  protected abstract void put(String key, String value);

  protected abstract void remove(String key);

  final void replace(String key, @Nullable String value) {
    if (value != null) {
      put(key, value);
    } else {
      remove(key);
    }
  }
}

package brave.internal.handler;

import brave.handler.FinishedSpanHandler;
import brave.handler.MutableSpan;
import brave.internal.Platform;
import brave.propagation.TraceContext;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FinishedSpanHandlers {
  public static FinishedSpanHandler compose(List<FinishedSpanHandler> finishedSpanHandlers) {
    if (finishedSpanHandlers == null) throw new NullPointerException("finishedSpanHandlers == null");
    int length = finishedSpanHandlers.size();
    if (length == 0) return FinishedSpanHandler.NOOP;

    // This composes by nesting to keep code small and avoid allocating iterators at handle() time
    FinishedSpanHandler result = FinishedSpanHandler.NOOP;
    for (int i = 0; i < length; i++) {
      FinishedSpanHandler next = finishedSpanHandlers.get(i);
      if (next == FinishedSpanHandler.NOOP) continue;
      if (result == FinishedSpanHandler.NOOP) {
        result = next;
        continue;
      }
      result = new CompositeFinishedSpanHandler(result, next);
    }
    return result;
  }

  /**
   * When {@code noop}, this drops input spans by returning false. Otherwise, it logs exceptions
   * instead of raising an error, as the supplied firehoseHandler could have bugs.
   */
  public static FinishedSpanHandler noopAware(FinishedSpanHandler handler, AtomicBoolean noop) {
    if (handler == FinishedSpanHandler.NOOP) return handler;
    return new NoopAwareFinishedSpan(handler, noop);
  }

  static final class NoopAwareFinishedSpan extends FinishedSpanHandler {
    final FinishedSpanHandler delegate;
    final AtomicBoolean noop;

    NoopAwareFinishedSpan(FinishedSpanHandler delegate, AtomicBoolean noop) {
      if (delegate == null) throw new NullPointerException("delegate == null");
      this.delegate = delegate;
      this.noop = noop;
    }

    @Override public boolean handle(TraceContext context, MutableSpan span) {
      if (noop.get()) return false;
      try {
        return delegate.handle(context, span);
      } catch (RuntimeException e) {
        Platform.get().log("error accepting {0}", context, e);
        return false;
      }
    }

    @Override public boolean alwaysSampleLocal() {
      return delegate.alwaysSampleLocal();
    }

    @Override public String toString() {
      return delegate.toString();
    }
  }

  static final class CompositeFinishedSpanHandler extends FinishedSpanHandler {
    final FinishedSpanHandler first, second;
    final boolean alwaysSampleLocal;

    CompositeFinishedSpanHandler(FinishedSpanHandler first, FinishedSpanHandler second) {
      this.first = first;
      this.second = second;
      this.alwaysSampleLocal = first.alwaysSampleLocal() || second.alwaysSampleLocal();
    }

    @Override public boolean handle(TraceContext context, MutableSpan span) {
      return first.handle(context, span) && second.handle(context, span);
    }

    @Override public boolean alwaysSampleLocal() {
      return alwaysSampleLocal;
    }

    @Override public String toString() {
      StringBuilder result = new StringBuilder();
      CompositeFinishedSpanHandler sequential = this;
      result.append(",").append(sequential.second);
      while (sequential.first instanceof CompositeFinishedSpanHandler) {
        sequential = (CompositeFinishedSpanHandler) sequential.first;
        result.insert(0, ",").insert(1, sequential.second);
      }
      result.insert(0, sequential.first);
      return result.insert(0, "CompositeFinishedSpanHandler(").append(")").toString();
    }
  }
}

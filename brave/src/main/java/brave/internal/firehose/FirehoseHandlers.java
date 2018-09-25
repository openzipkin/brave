package brave.internal.firehose;

import brave.firehose.FirehoseHandler;
import brave.firehose.MutableSpan;
import brave.internal.Platform;
import brave.propagation.TraceContext;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FirehoseHandlers {
  public static FirehoseHandler compose(List<FirehoseHandler> firehoseHandlers) {
    if (firehoseHandlers == null) throw new NullPointerException("firehoseHandlers == null");
    int length = firehoseHandlers.size();
    if (length == 0) return FirehoseHandler.NOOP;

    // This composes by nesting to keep code small and avoid allocating iterators at handle() time
    FirehoseHandler result = FirehoseHandler.NOOP;
    for (int i = 0; i < length; i++) {
      FirehoseHandler next = firehoseHandlers.get(i);
      if (next == FirehoseHandler.NOOP) continue;
      if (result == FirehoseHandler.NOOP) {
        result = next;
        continue;
      }
      result = new CompositeFirehoseHandler(result, next);
    }
    return result;
  }

  /**
   * When {@code noop}, this drops input spans by returning false. Otherwise, it logs exceptions
   * instead of raising an error, as the supplied firehoseHandler could have bugs.
   */
  public static FirehoseHandler noopAware(FirehoseHandler handler, AtomicBoolean noop) {
    if (handler == FirehoseHandler.NOOP) return handler;
    return new NoopAwareFirehose(handler, noop);
  }

  static final class NoopAwareFirehose extends FirehoseHandler {
    final FirehoseHandler delegate;
    final AtomicBoolean noop;

    NoopAwareFirehose(FirehoseHandler delegate, AtomicBoolean noop) {
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

  static final class CompositeFirehoseHandler extends FirehoseHandler {
    final FirehoseHandler first, second;
    final boolean alwaysSampleLocal;

    CompositeFirehoseHandler(FirehoseHandler first, FirehoseHandler second) {
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
      CompositeFirehoseHandler sequential = this;
      result.append(",").append(sequential.second);
      while (sequential.first instanceof CompositeFirehoseHandler) {
        sequential = (CompositeFirehoseHandler) sequential.first;
        result.insert(0, ",").insert(1, sequential.second);
      }
      result.insert(0, sequential.first);
      return result.insert(0, "CompositeFirehoseHandler(").append(")").toString();
    }
  }
}

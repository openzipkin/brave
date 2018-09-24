package brave.internal.firehose;

import brave.firehose.FirehoseHandler;
import brave.firehose.MutableSpan;
import brave.internal.Platform;
import brave.propagation.TraceContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FirehoseHandlers {
  public static FirehoseHandler.Factory constantFactory(FirehoseHandler firehoseHandler) {
    if (firehoseHandler == null) throw new NullPointerException("firehoseHandler == null");
    return new FirehoseHandler.Factory() {
      @Override public FirehoseHandler create(String serviceName, String ip, int port) {
        return firehoseHandler;
      }

      @Override public String toString() {
        return "FirehoseHandlerFactory(" + firehoseHandler + ")";
      }
    };
  }

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

  public static List<FirehoseHandler> create(List<FirehoseHandler.Factory> firehoseHandlerFactories,
      String serviceName, String ip, int port) {
    if (firehoseHandlerFactories.isEmpty()) return Collections.emptyList();

    List<FirehoseHandler> result = new ArrayList<>();
    for (FirehoseHandler.Factory factory : firehoseHandlerFactories) {
      FirehoseHandler next = factory.create(serviceName, ip, port);
      if (next == null) throw new NullPointerException(factory + " created null");
      if (next == FirehoseHandler.NOOP) continue;
      result.add(next);
    }
    return result;
  }

  /**
   * logs exceptions instead of raising an error, as the supplied firehoseHandler could have bugs
   */
  public static FirehoseHandler noopAware(FirehoseHandler handler, AtomicBoolean noop) {
    if (handler == FirehoseHandler.NOOP) return handler;
    return new NoopAwareFirehose(handler, noop);
  }

  static final class NoopAwareFirehose implements FirehoseHandler {
    final FirehoseHandler delegate;
    final AtomicBoolean noop;

    NoopAwareFirehose(FirehoseHandler delegate, AtomicBoolean noop) {
      if (delegate == null) throw new NullPointerException("delegate == null");
      this.delegate = delegate;
      this.noop = noop;
    }

    @Override public void handle(TraceContext context, MutableSpan span) {
      if (noop.get()) return;
      try {
        delegate.handle(context, span);
      } catch (RuntimeException e) {
        Platform.get().log("error accepting {0}", context, e);
      }
    }

    @Override public String toString() {
      return delegate.toString();
    }
  }

  static final class CompositeFirehoseHandler implements FirehoseHandler {
    final FirehoseHandler first, second;

    CompositeFirehoseHandler(FirehoseHandler first, FirehoseHandler second) {
      this.first = first;
      this.second = second;
    }

    @Override public void handle(TraceContext context, MutableSpan span) {
      first.handle(context, span);
      second.handle(context, span);
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

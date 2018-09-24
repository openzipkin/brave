package brave.internal.firehose;

import brave.firehose.FirehoseHandler;
import brave.firehose.MutableSpan;
import brave.internal.Platform;
import brave.propagation.TraceContext;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FirehoseHandlers {

  public static FirehoseHandler compose(FirehoseHandler first, FirehoseHandler second) {
    return new SplitFirehose(first, second);
  }

  public static FirehoseHandler compose(List<FirehoseHandler.Factory> firehoseHandlerFactories,
      String serviceName, String ip, int port) {
    FirehoseHandler firehoseHandler = FirehoseHandler.NOOP;
    for (FirehoseHandler.Factory factory : firehoseHandlerFactories) {
      FirehoseHandler next = factory.create(serviceName, ip, port);
      if (next == FirehoseHandler.NOOP) continue;
      if (firehoseHandler == FirehoseHandler.NOOP) {
        firehoseHandler = next;
      } else if (!(firehoseHandler instanceof SplitFirehose)) {
        firehoseHandler = new SplitFirehose(firehoseHandler, next);
      } else {
        throw new UnsupportedOperationException(
            "more than two firehose handlers aren't yet supported");
      }
    }
    return firehoseHandler;
  }

  /**
   * logs exceptions instead of raising an error, as the supplied firehoseHandler could have bugs
   */
  public static FirehoseHandler noopAware(FirehoseHandler handler, AtomicBoolean noop) {
    return new NoopAwareFirehose(handler, noop);
  }

  static final class NoopAwareFirehose implements FirehoseHandler {
    final FirehoseHandler delegate;
    final AtomicBoolean noop;

    NoopAwareFirehose(FirehoseHandler delegate, AtomicBoolean noop) {
      this.delegate = delegate;
      this.noop = noop;
    }

    @Override public void accept(TraceContext context, MutableSpan span) {
      if (noop.get()) return;
      try {
        delegate.accept(context, span);
      } catch (RuntimeException e) {
        Platform.get().log("error accepting {0}", context, e);
      }
    }

    @Override public String toString() {
      return delegate.toString();
    }
  }

  static final class SplitFirehose implements FirehoseHandler {
    final FirehoseHandler first, second;

    SplitFirehose(FirehoseHandler first, FirehoseHandler second) {
      this.first = first;
      this.second = second;
    }

    @Override public void accept(TraceContext context, MutableSpan span) {
      first.accept(context, span);
      second.accept(context, span);
    }

    @Override public String toString() {
      return "SplitFirehoseHandler(" + first + ", " + second + ")";
    }
  }
}

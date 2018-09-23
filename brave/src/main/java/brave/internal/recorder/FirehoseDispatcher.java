package brave.internal.recorder;

import brave.ErrorParser;
import brave.firehose.FirehoseHandler;
import brave.firehose.MutableSpan;
import brave.internal.Platform;
import brave.propagation.TraceContext;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

/**
 * Dispatches {@link TraceContext#sampled() sampled} spans to Zipkin after forwarding to any default
 * firehoseHandler.
 */
public final class FirehoseDispatcher {

  final FirehoseHandler firehoseHandler;
  final AtomicBoolean noop = new AtomicBoolean();
  final boolean alwaysSampleLocal;
  final SampledToZipkinFirehose zipkinFirehose;

  public FirehoseDispatcher(List<FirehoseHandler.Factory> firehoseHandlerFactories,
      ErrorParser errorParser, Reporter<Span> spanReporter, String serviceName, String ip,
      int port) {
    boolean alwaysSampleLocal = false;
    for (FirehoseHandler.Factory factory : firehoseHandlerFactories) {
      if (factory.alwaysSampleLocal()) alwaysSampleLocal = true;
    }
    this.alwaysSampleLocal = alwaysSampleLocal;

    FirehoseHandler firehoseHandler =
        newFirehoseHandler(firehoseHandlerFactories, serviceName, ip, port);
    if (spanReporter != Reporter.NOOP) {
      zipkinFirehose =
          new SampledToZipkinFirehose(errorParser, spanReporter, serviceName, ip, port);
      if (firehoseHandler != FirehoseHandler.NOOP) {
        firehoseHandler = new SplitFirehose(firehoseHandler, zipkinFirehose);
      } else {
        firehoseHandler = zipkinFirehose;
      }
    } else {
      zipkinFirehose = null;
    }
    this.firehoseHandler = firehoseHandler == FirehoseHandler.NOOP ? firehoseHandler
        : new NoopAwareFirehose(firehoseHandler, noop);
  }

  /** Returns a firehoseHandler that accepts data according to configuration */
  public FirehoseHandler firehose() {
    return firehoseHandler;
  }

  /** @see FirehoseHandler.Factory#alwaysSampleLocal() */
  public boolean alwaysSampleLocal() {
    return alwaysSampleLocal;
  }

  // NOTE: this is a special method which accepts zipkin2.Span.Builder which is not normal, but
  // needed for flushing incomplete data. Revisit at some point.
  void reportIncompleteToZipkin(MutableSpan state, Span.Builder builderWithContextData) {
    if (zipkinFirehose == null) return;
    zipkinFirehose.report(state, builderWithContextData);
  }

  /**
   * logs exceptions instead of raising an error, as the supplied firehoseHandler could have bugs
   */
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

  /** logs exceptions instead of raising an error, as the supplied reporter could have bugs */
  static final class SampledToZipkinFirehose implements FirehoseHandler {
    final Reporter<zipkin2.Span> spanReporter;
    final MutableSpanConverter converter;

    SampledToZipkinFirehose(ErrorParser errorParser, Reporter<Span> spanReporter,
        String serviceName, String ip, int port) {
      this.spanReporter = spanReporter;
      this.converter = new MutableSpanConverter(errorParser, serviceName, ip, port);
    }

    @Override public void accept(TraceContext context, MutableSpan span) {
      if (!Boolean.TRUE.equals(context.sampled())) return;

      Span.Builder builderWithContextData = Span.newBuilder()
          .traceId(context.traceIdHigh(), context.traceId())
          .parentId(context.parentIdAsLong())
          .id(context.spanId());
      if (context.debug()) builderWithContextData.debug(true);

      report(span, builderWithContextData);
    }

    void report(MutableSpan span, Span.Builder builderWithContextData) {
      try {
        converter.convert(span, builderWithContextData);
        spanReporter.report(builderWithContextData.build());
      } catch (RuntimeException e) {
        Platform.get().log("error reporting {0}", span, e);
      }
    }

    @Override public String toString() {
      return spanReporter.toString();
    }
  }

  public AtomicBoolean noop() {
    return noop;
  }

  @Override public String toString() {
    return firehoseHandler.toString();
  }

  static FirehoseHandler newFirehoseHandler(List<FirehoseHandler.Factory> firehoseHandlerFactories,
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
}

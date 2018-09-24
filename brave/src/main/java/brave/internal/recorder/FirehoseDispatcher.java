package brave.internal.recorder;

import brave.ErrorParser;
import brave.firehose.FirehoseHandler;
import brave.firehose.MutableSpan;
import brave.internal.Platform;
import brave.internal.firehose.FirehoseHandlers;
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
        FirehoseHandlers.compose(firehoseHandlerFactories, serviceName, ip, port);
    if (spanReporter != Reporter.NOOP) {
      zipkinFirehose =
          new SampledToZipkinFirehose(errorParser, spanReporter, serviceName, ip, port);
      if (firehoseHandler != FirehoseHandler.NOOP) {
        firehoseHandler = FirehoseHandlers.compose(firehoseHandler, zipkinFirehose);
      } else {
        firehoseHandler = zipkinFirehose;
      }
    } else {
      zipkinFirehose = null;
    }
    this.firehoseHandler = firehoseHandler == FirehoseHandler.NOOP ? firehoseHandler
        : FirehoseHandlers.noopAware(firehoseHandler, noop);
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
}

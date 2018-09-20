package brave.internal.recorder;

import brave.firehose.Firehose;
import brave.firehose.MutableSpan;
import brave.internal.Platform;
import brave.propagation.TraceContext;
import java.util.concurrent.atomic.AtomicBoolean;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

/**
 * Dispatches {@link TraceContext#sampled() sampled} spans to Zipkin after forwarding to any default
 * firehose.
 */
public final class FirehoseDispatcher {

  final Firehose firehose;
  final AtomicBoolean noop = new AtomicBoolean();
  final SampledToZipkinFirehose zipkinFirehose;

  public FirehoseDispatcher(Firehose.Factory firehoseFactory, Reporter<Span> spanReporter,
      String serviceName, String ip, int port) {
    Firehose firehose = firehoseFactory.create(serviceName, ip, port);
    if (spanReporter != Reporter.NOOP) {
      zipkinFirehose = new SampledToZipkinFirehose(spanReporter, serviceName, ip, port);
      if (firehose != Firehose.NOOP) {
        firehose = new SplitFirehose(firehose, zipkinFirehose);
      } else {
        firehose = zipkinFirehose;
      }
    } else {
      zipkinFirehose = null;
    }
    this.firehose = firehose == Firehose.NOOP ? firehose : new NoopAwareFirehose(firehose, noop);
  }

  /** Returns a firehose that accepts data according to configuration */
  public Firehose firehose() {
    return firehose;
  }

  /** Internal method. do not use */
  public void reportIncompleteToZipkin(MutableSpan state, Span.Builder builderWithContextData) {
    if (zipkinFirehose == null) return;
    zipkinFirehose.report(state, builderWithContextData);
  }

  /**
   * logs exceptions instead of raising an error, as the supplied firehose could have bugs
   */
  static final class NoopAwareFirehose implements Firehose {
    final Firehose delegate;
    final AtomicBoolean noop;

    NoopAwareFirehose(Firehose delegate, AtomicBoolean noop) {
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

  static final class SplitFirehose implements Firehose {
    final Firehose first, second;

    SplitFirehose(Firehose first, Firehose second) {
      this.first = first;
      this.second = second;
    }

    @Override public void accept(TraceContext context, MutableSpan span) {
      first.accept(context, span);
      second.accept(context, span);
    }

    @Override public String toString() {
      return "SplitFirehose(" + first + ", " + second + ")";
    }
  }

  /** logs exceptions instead of raising an error, as the supplied reporter could have bugs */
  static final class SampledToZipkinFirehose implements Firehose {
    final Reporter<zipkin2.Span> spanReporter;
    final MutableSpanConverter converter;

    SampledToZipkinFirehose(Reporter<Span> spanReporter, String serviceName, String ip, int port) {
      this.spanReporter = spanReporter;
      this.converter = new MutableSpanConverter(serviceName, ip, port);
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
    return firehose.toString();
  }
}

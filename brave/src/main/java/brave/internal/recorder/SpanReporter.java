package brave.internal.recorder;

import brave.internal.Nullable;
import brave.internal.Platform;
import brave.propagation.TraceContext;
import java.util.concurrent.atomic.AtomicBoolean;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

public class SpanReporter implements Firehose {

  public static final class Factory extends Firehose.Factory {
    @Nullable final Firehose.Factory delegate;
    final Reporter<zipkin2.Span> reporter;
    final AtomicBoolean noop;

    public Factory(Firehose.Factory delegate, Reporter<Span> reporter, AtomicBoolean noop) {
      this.delegate = delegate;
      this.reporter = reporter;
      this.noop = noop;
    }

    @Override public boolean alwaysSampleLocal() {
      return delegate.alwaysSampleLocal();
    }

    @Override public SpanReporter create(String serviceName, String ip, int port) {
      MutableSpanConverter converter = new MutableSpanConverter(serviceName, ip, port);
      SpanReporter result = new SpanReporter(converter, reporter, noop);
      if (delegate == null) return result;
      return new FirehoseSpanReporter(result, delegate.create(serviceName, ip, port));
    }
  }

  static final class FirehoseSpanReporter extends SpanReporter {
    final Firehose firehose;

    FirehoseSpanReporter(SpanReporter delegate, Firehose firehose) {
      super(delegate.converter, delegate.zipkinReporter, delegate.noop);
      this.firehose = firehose;
    }

    @Override public void accept(TraceContext context, MutableSpan span) {
      if (noop.get()) return;
      firehose.accept(context, span);
      if (zipkinReporter == Reporter.NOOP) return;
      super.report(context, span);
    }
  }

  final Reporter<zipkin2.Span> zipkinReporter;
  final MutableSpanConverter converter;
  final MutableSpan defaultSpan;
  final AtomicBoolean noop;

  SpanReporter(
      MutableSpanConverter converter,
      Reporter<zipkin2.Span> zipkinReporter,
      AtomicBoolean noop
  ) {
    this.converter = converter;
    this.zipkinReporter = zipkinReporter;
    this.defaultSpan = new MutableSpan();
    this.defaultSpan.localServiceName(converter.localServiceName);
    this.defaultSpan.localIp(converter.localIp);
    this.defaultSpan.localPort(converter.localPort);
    this.noop = noop;
  }

  MutableSpan newMutableSpan() {
    return new MutableSpan(defaultSpan);
  }

  @Override public void accept(TraceContext context, MutableSpan span) {
    if (zipkinReporter == Reporter.NOOP || noop.get()) return;
    report(context, span);
  }

  // avoids having to re-read the atomic boolean
  void report(TraceContext context, MutableSpan span) {
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
      zipkinReporter.report(builderWithContextData.build());
    } catch (RuntimeException e) {
      Platform.get().log("error reporting {0}", span, e);
    }
  }

  @Override public String toString() {
    return zipkinReporter.toString();
  }
}

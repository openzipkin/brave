package brave.internal.recorder;

import brave.internal.Platform;
import brave.propagation.TraceContext;
import java.util.concurrent.atomic.AtomicBoolean;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

public final class SpanReporter implements Reporter<Span> {
  final Endpoint localEndpoint;
  final Reporter<zipkin2.Span> delegate;
  final MutableSpanConverter converter = new MutableSpanConverter();
  final AtomicBoolean noop;

  public SpanReporter(Endpoint localEndpoint, Reporter<zipkin2.Span> delegate, AtomicBoolean noop) {
    this.localEndpoint = localEndpoint;
    this.delegate = delegate;
    this.noop = noop;
  }

  public void report(TraceContext context, MutableSpan span) {
    zipkin2.Span.Builder builderWithContextData = zipkin2.Span.newBuilder()
        .traceId(context.traceIdHigh(), context.traceId())
        .parentId(context.parentIdAsLong())
        .id(context.spanId())
        .debug(context.debug())
        .localEndpoint(localEndpoint);

    converter.convert(span, builderWithContextData);
    report(builderWithContextData.build());
  }

  @Override public void report(zipkin2.Span span) {
    if (noop.get()) return;
    try {
      delegate.report(span);
    } catch (RuntimeException e) {
      Platform.get().log("error reporting {0}", span, e);
    }
  }

  @Override public String toString() {
    return delegate.toString();
  }
}

package brave.internal.recorder;

import brave.propagation.TraceContext;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

public final class SpanReporter implements Reporter<Span> {
  static final Logger logger = Logger.getLogger(SpanReporter.class.getName());

  final Endpoint localEndpoint;
  final Reporter<zipkin2.Span> delegate;
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

    MutableSpanConverter.convert(span, builderWithContextData);
    report(builderWithContextData.build());
  }

  @Override public void report(zipkin2.Span span) {
    if (noop.get()) return;
    try {
      delegate.report(span);
    } catch (RuntimeException e) {
      if (logger.isLoggable(Level.FINE)) { // fine level to not fill logs
        logger.log(Level.FINE, "error reporting " + span, e);
      }
    }
  }

  @Override public String toString() {
    return delegate.toString();
  }
}

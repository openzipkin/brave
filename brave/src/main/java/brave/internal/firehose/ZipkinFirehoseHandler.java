package brave.internal.firehose;

import brave.ErrorParser;
import brave.firehose.FirehoseHandler;
import brave.firehose.MutableSpan;
import brave.propagation.TraceContext;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

/** logs exceptions instead of raising an error, as the supplied reporter could have bugs */
public final class ZipkinFirehoseHandler implements FirehoseHandler {
  final Reporter<zipkin2.Span> spanReporter;
  final MutableSpanConverter converter;

  public ZipkinFirehoseHandler(Reporter<zipkin2.Span> spanReporter,
      ErrorParser errorParser, String serviceName, String ip, int port) {
    this.spanReporter = spanReporter;
    this.converter = new MutableSpanConverter(errorParser, serviceName, ip, port);
  }

  @Override public void handle(TraceContext context, MutableSpan span) {
    if (!Boolean.TRUE.equals(context.sampled())) return;

    Span.Builder builderWithContextData = Span.newBuilder()
        .traceId(context.traceIdHigh(), context.traceId())
        .parentId(context.parentIdAsLong())
        .id(context.spanId());
    if (context.debug()) builderWithContextData.debug(true);

    converter.convert(span, builderWithContextData);
    spanReporter.report(builderWithContextData.build());
  }

  @Override public String toString() {
    return spanReporter.toString();
  }
}

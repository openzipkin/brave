package brave.internal.handler;

import brave.ErrorParser;
import brave.handler.FinishedSpanHandler;
import brave.handler.MutableSpan;
import brave.propagation.TraceContext;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

/** logs exceptions instead of raising an error, as the supplied reporter could have bugs */
public final class ZipkinFinishedSpanHandler extends FinishedSpanHandler {
  final Reporter<zipkin2.Span> spanReporter;
  final MutableSpanConverter converter;

  public ZipkinFinishedSpanHandler(Reporter<zipkin2.Span> spanReporter,
      ErrorParser errorParser, String serviceName, String ip, int port) {
    this.spanReporter = spanReporter;
    this.converter = new MutableSpanConverter(errorParser, serviceName, ip, port);
  }

  @Override public boolean handle(TraceContext context, MutableSpan span) {
    if (!Boolean.TRUE.equals(context.sampled())) return true;

    Span.Builder builderWithContextData = Span.newBuilder()
        .traceId(context.traceIdString())
        .parentId(context.parentIdString())
        .id(context.spanIdString());
    if (context.debug()) builderWithContextData.debug(true);

    converter.convert(span, builderWithContextData);
    spanReporter.report(builderWithContextData.build());
    return true;
  }

  @Override public String toString() {
    return spanReporter.toString();
  }
}

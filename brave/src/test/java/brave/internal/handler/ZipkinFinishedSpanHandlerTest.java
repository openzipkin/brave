package brave.internal.handler;

import brave.ErrorParser;
import brave.handler.MutableSpan;
import brave.propagation.TraceContext;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

import static org.assertj.core.api.Assertions.assertThat;

public class ZipkinFinishedSpanHandlerTest {
  List<Span> spans = new ArrayList<>();
  ZipkinFinishedSpanHandler zipkinFinishedSpanHandler;

  @Before public void init() {
    init(spans::add);
  }

  void init(Reporter<Span> spanReporter) {
    zipkinFinishedSpanHandler = new ZipkinFinishedSpanHandler(spanReporter, new ErrorParser(),
        "favistar", "1.2.3.4", 0);
  }

  @Test public void reportsSampledSpan() {
    TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true).build();
    zipkinFinishedSpanHandler.handle(context, new MutableSpan());

    assertThat(spans.get(0)).isEqualToComparingFieldByField(
        Span.newBuilder()
            .traceId("1")
            .id("2")
            .localEndpoint(zipkinFinishedSpanHandler.converter.localEndpoint)
            .build()
    );
  }

  @Test public void reportsDebugSpan() {
    TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).debug(true).build();
    zipkinFinishedSpanHandler.handle(context, new MutableSpan());

    assertThat(spans.get(0)).isEqualToComparingFieldByField(
        Span.newBuilder()
            .traceId("1")
            .id("2")
            .debug(true)
            .localEndpoint(zipkinFinishedSpanHandler.converter.localEndpoint)
            .build()
    );
  }

  @Test public void doesntReportUnsampledSpan() {
    TraceContext context =
        TraceContext.newBuilder().traceId(1).spanId(2).sampled(false).sampledLocal(true).build();
    zipkinFinishedSpanHandler.handle(context, new MutableSpan());

    assertThat(spans).isEmpty();
  }
}

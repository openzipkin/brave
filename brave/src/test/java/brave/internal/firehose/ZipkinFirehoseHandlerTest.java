package brave.internal.firehose;

import brave.ErrorParser;
import brave.firehose.MutableSpan;
import brave.propagation.TraceContext;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

import static org.assertj.core.api.Assertions.assertThat;

public class ZipkinFirehoseHandlerTest {
  List<Span> spans = new ArrayList<>();
  ZipkinFirehoseHandler zipkinFirehoseHandler;

  @Before public void init() {
    init(spans::add);
  }

  void init(Reporter<Span> spanReporter) {
    zipkinFirehoseHandler = new ZipkinFirehoseHandler(spanReporter, new ErrorParser(),
        "favistar", "1.2.3.4", 0);
  }

  @Test public void reportsSampledSpan() {
    TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true).build();
    zipkinFirehoseHandler.handle(context, new MutableSpan());

    assertThat(spans.get(0)).isEqualToComparingFieldByField(
        Span.newBuilder()
            .traceId("1")
            .id("2")
            .localEndpoint(zipkinFirehoseHandler.converter.localEndpoint)
            .build()
    );
  }

  @Test public void reportsDebugSpan() {
    TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).debug(true).build();
    zipkinFirehoseHandler.handle(context, new MutableSpan());

    assertThat(spans.get(0)).isEqualToComparingFieldByField(
        Span.newBuilder()
            .traceId("1")
            .id("2")
            .debug(true)
            .localEndpoint(zipkinFirehoseHandler.converter.localEndpoint)
            .build()
    );
  }

  @Test public void doesntReportUnsampledSpan() {
    TraceContext context =
        TraceContext.newBuilder().traceId(1).spanId(2).sampled(false).sampledLocal(true).build();
    zipkinFirehoseHandler.handle(context, new MutableSpan());

    assertThat(spans).isEmpty();
  }
}

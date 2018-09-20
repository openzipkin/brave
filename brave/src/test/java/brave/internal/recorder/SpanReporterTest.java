package brave.internal.recorder;

import brave.propagation.TraceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

import static org.assertj.core.api.Assertions.assertThat;

public class SpanReporterTest {
  List<Span> spans = new ArrayList<>();

  MutableSpanConverter converter = new MutableSpanConverter("unknown", "127.0.0.1", 0);
  SpanReporter reporter = new SpanReporter(converter, spans::add, new AtomicBoolean());

  @Test public void reportsSampledSpanToZipkin() {
    TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true).build();
    reporter.accept(context, new MutableSpan());

    assertThat(spans.get(0)).isEqualToComparingFieldByField(
        Span.newBuilder().traceId("1").id("2").localEndpoint(converter.localEndpoint).build()
    );
  }

  @Test public void reportsDebugSpanToZipkin() {
    TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).debug(true).build();
    reporter.accept(context, new MutableSpan());

    assertThat(spans.get(0)).isEqualToComparingFieldByField(
        Span.newBuilder()
            .traceId("1")
            .id("2")
            .debug(true)
            .localEndpoint(converter.localEndpoint)
            .build()
    );
  }

  @Test public void doesntReportUnsampledSpanToZipkin() {
    TraceContext context =
        TraceContext.newBuilder().traceId(1).spanId(2).sampled(false).sampledLocal(true).build();
    reporter.accept(context, new MutableSpan());

    assertThat(spans).isEmpty();
  }

  @Test public void doesntReportToZipkinWhenReporterIsNoop() {
    SpanReporter reporter = new SpanReporter(converter, Reporter.NOOP, new AtomicBoolean()) {
      @Override void report(MutableSpan span, Span.Builder builderWithContextData) {
        throw new AssertionError();
      }
    };

    TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true).build();
    reporter.accept(context, new MutableSpan());
  }

  @Test public void doesntReportWhenNoop() {
    reporter.noop.set(true);
    TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true).build();
    reporter.accept(context, new MutableSpan());

    assertThat(spans).isEmpty();
  }

  @Test public void doesntCrashOnReporterError() {
    SpanReporter reporter = new SpanReporter(converter, s -> {
      throw new RuntimeException();
    }, new AtomicBoolean());

    TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true).build();
    reporter.accept(context, new MutableSpan());
  }
}

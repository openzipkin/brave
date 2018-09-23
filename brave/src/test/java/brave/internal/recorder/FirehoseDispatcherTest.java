package brave.internal.recorder;

import brave.ErrorParser;
import brave.firehose.FirehoseHandler;
import brave.firehose.MutableSpan;
import brave.propagation.TraceContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

import static org.assertj.core.api.Assertions.assertThat;

public class FirehoseDispatcherTest {
  List<MutableSpan> mutableSpans = new ArrayList<>();
  FirehoseHandler.Factory testFirehoseFactory = new FirehoseHandler.Factory() {
    @Override public FirehoseHandler create(String serviceName, String ip, int port) {
      return new FirehoseHandler() {
        @Override public void accept(TraceContext c, MutableSpan s) {
          mutableSpans.add(s);
        }

        @Override public String toString() {
          return "TestFirehoseHandler{}";
        }
      };
    }
  };
  List<Span> spans = new ArrayList<>();
  FirehoseDispatcher firehoseDispatcher;
  FirehoseHandler firehoseHandler;
  Endpoint localEndpoint;

  @Before public void init() {
    init(Collections.emptyList(), spans::add);
  }

  void init(List<FirehoseHandler.Factory> factories, Reporter<Span> spanReporter) {
    firehoseDispatcher = new FirehoseDispatcher(factories, new ErrorParser(), spanReporter,
        "favistar", "1.2.3.4", 0);
    firehoseHandler = firehoseDispatcher.firehose();
    localEndpoint = firehoseDispatcher.zipkinFirehose != null
        ? firehoseDispatcher.zipkinFirehose.converter.localEndpoint : null;
  }

  @Test public void reportsSampledSpanToZipkin() {
    TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true).build();
    firehoseHandler.accept(context, new MutableSpan());

    assertThat(spans.get(0)).isEqualToComparingFieldByField(
        Span.newBuilder().traceId("1").id("2").localEndpoint(localEndpoint).build()
    );
  }

  @Test public void reportsDebugSpanToZipkin() {
    TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).debug(true).build();
    firehoseHandler.accept(context, new MutableSpan());

    assertThat(spans.get(0)).isEqualToComparingFieldByField(
        Span.newBuilder()
            .traceId("1")
            .id("2")
            .debug(true)
            .localEndpoint(localEndpoint)
            .build()
    );
  }

  @Test public void doesntReportUnsampledSpanToZipkin() {
    TraceContext context =
        TraceContext.newBuilder().traceId(1).spanId(2).sampled(false).sampledLocal(true).build();
    firehoseHandler.accept(context, new MutableSpan());

    assertThat(spans).isEmpty();
  }

  @Test public void noopWhenBothFirehoseAreNoop() {
    init(Collections.emptyList(), Reporter.NOOP);

    assertThat(firehoseHandler).hasToString("NoopFirehoseHandler{}");

    assertThat(firehoseDispatcher.firehose())
        .isSameAs(FirehoseHandler.NOOP);
  }

  @Test public void splitWhenFirehosePresent() {
    init(Collections.singletonList(testFirehoseFactory), new Reporter<Span>() {
      @Override public void report(Span span) {
        spans.add(span);
      }

      @Override public String toString() {
        return "TestReporter()";
      }
    });

    assertThat(firehoseHandler)
        .hasToString("SplitFirehoseHandler(TestFirehoseHandler{}, TestReporter())");

    TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true).build();
    firehoseHandler.accept(context, new MutableSpan());

    assertThat(spans).hasSize(1);
    assertThat(mutableSpans).hasSize(1);
  }

  @Test public void notSplitWhenZipkinIsNoop() {
    init(Collections.singletonList(testFirehoseFactory), Reporter.NOOP);

    assertThat(firehoseHandler).hasToString("TestFirehoseHandler{}");

    TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true).build();
    firehoseHandler.accept(context, new MutableSpan());

    assertThat(mutableSpans).hasSize(1);
  }

  @Test public void doesntReportToZipkinWhenNoop() {
    firehoseDispatcher.noop.set(true);

    TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true).build();
    firehoseHandler.accept(context, new MutableSpan());

    assertThat(spans).isEmpty();
  }

  @Test public void doesntCrashOnFirehoseDispatcherError() {
    init(Collections.singletonList(new FirehoseHandler.Factory() {
      @Override public FirehoseHandler create(String serviceName, String ip, int port) {
        return (c, s) -> {
          throw new RuntimeException();
        };
      }
    }), spans::add);

    TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true).build();
    firehoseHandler.accept(context, new MutableSpan());

    assertThat(spans).hasSize(0); // firehoseHandler crash invalidates spans for zipkin
  }

  @Test public void doesntCrashOnReporterError() {
    List<MutableSpan> mutableSpans = new ArrayList<>();
    init(Collections.singletonList(new FirehoseHandler.Factory() {
      @Override public FirehoseHandler create(String serviceName, String ip, int port) {
        return (c, s) -> mutableSpans.add(s);
      }
    }), s -> {
      throw new RuntimeException();
    });

    TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true).build();
    firehoseHandler.accept(context, new MutableSpan());

    assertThat(mutableSpans).hasSize(1);
  }
}

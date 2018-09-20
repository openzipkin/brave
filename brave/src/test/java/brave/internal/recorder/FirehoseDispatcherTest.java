package brave.internal.recorder;

import brave.firehose.Firehose;
import brave.firehose.MutableSpan;
import brave.propagation.TraceContext;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

import static org.assertj.core.api.Assertions.assertThat;

public class FirehoseDispatcherTest {
  List<MutableSpan> mutableSpans = new ArrayList<>();
  Firehose.Factory testFirehoseFactory = new Firehose.Factory() {
    @Override public Firehose create(String serviceName, String ip, int port) {
      return new Firehose() {
        @Override public void accept(TraceContext c, MutableSpan s) {
          mutableSpans.add(s);
        }

        @Override public String toString() {
          return "TestFirehose{}";
        }
      };
    }
  };
  List<Span> spans = new ArrayList<>();
  FirehoseDispatcher firehoseDispatcher;
  Firehose firehose;
  Endpoint localEndpoint;

  @Before public void init() {
    init(new Firehose.Factory() {
      @Override public Firehose create(String serviceName, String ip, int port) {
        return Firehose.NOOP;
      }
    }, spans::add);
  }

  void init(Firehose.Factory delegate, Reporter<Span> spanReporter) {
    firehoseDispatcher = new FirehoseDispatcher(delegate, spanReporter,
        "favistar", "1.2.3.4", 0);
    firehose = firehoseDispatcher.firehose();
    localEndpoint = firehoseDispatcher.zipkinFirehose != null
        ? firehoseDispatcher.zipkinFirehose.converter.localEndpoint : null;
  }

  @Test public void reportsSampledSpanToZipkin() {
    TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true).build();
    firehose.accept(context, new MutableSpan());

    assertThat(spans.get(0)).isEqualToComparingFieldByField(
        Span.newBuilder().traceId("1").id("2").localEndpoint(localEndpoint).build()
    );
  }

  @Test public void reportsDebugSpanToZipkin() {
    TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).debug(true).build();
    firehose.accept(context, new MutableSpan());

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
    firehose.accept(context, new MutableSpan());

    assertThat(spans).isEmpty();
  }

  @Test public void noopWhenBothFirehoseAreNoop() {
    init(new Firehose.Factory() {
      @Override public Firehose create(String serviceName, String ip, int port) {
        return Firehose.NOOP;
      }
    }, Reporter.NOOP);

    assertThat(firehose).hasToString("NoopFirehose{}");

    assertThat(firehoseDispatcher.firehose())
        .isSameAs(Firehose.NOOP);
  }

  @Test public void splitWhenFirehosePresent() {
    init(testFirehoseFactory, new Reporter<Span>() {
      @Override public void report(Span span) {
        spans.add(span);
      }

      @Override public String toString() {
        return "TestReporter()";
      }
    });

    assertThat(firehose).hasToString("SplitFirehose(TestFirehose{}, TestReporter())");

    TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true).build();
    firehose.accept(context, new MutableSpan());

    assertThat(spans).hasSize(1);
    assertThat(mutableSpans).hasSize(1);
  }

  @Test public void notSplitWhenZipkinIsNoop() {
    init(testFirehoseFactory, Reporter.NOOP);

    assertThat(firehose).hasToString("TestFirehose{}");

    TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true).build();
    firehose.accept(context, new MutableSpan());

    assertThat(mutableSpans).hasSize(1);
  }

  @Test public void doesntReportToZipkinWhenNoop() {
    firehoseDispatcher.noop.set(true);

    TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true).build();
    firehose.accept(context, new MutableSpan());

    assertThat(spans).isEmpty();
  }

  @Test public void doesntCrashOnFirehoseDispatcherError() {
    init(new Firehose.Factory() {
      @Override public Firehose create(String serviceName, String ip, int port) {
        return (c, s) -> {
          throw new RuntimeException();
        };
      }
    }, spans::add);

    TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true).build();
    firehose.accept(context, new MutableSpan());

    assertThat(spans).hasSize(0); // firehose crash invalidates spans for zipkin
  }

  @Test public void doesntCrashOnReporterError() {
    List<MutableSpan> mutableSpans = new ArrayList<>();
    init(new Firehose.Factory() {
      @Override public Firehose create(String serviceName, String ip, int port) {
        return (c, s) -> mutableSpans.add(s);
      }
    }, s -> {
      throw new RuntimeException();
    });

    TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).sampled(true).build();
    firehose.accept(context, new MutableSpan());

    assertThat(mutableSpans).hasSize(1);
  }
}

package brave;

import brave.handler.FinishedSpanHandler;
import brave.handler.MutableSpan;
import brave.propagation.B3SinglePropagation;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.sampler.Sampler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

import static org.assertj.core.api.Assertions.assertThat;

public class TracingTest {
  List<zipkin2.Span> spans = new ArrayList<>();
  List<MutableSpan> mutableSpans = new ArrayList<>();
  FinishedSpanHandler finishedSpanHandler = new FinishedSpanHandler() {
    @Override public boolean handle(TraceContext context, MutableSpan span) {
      mutableSpans.add(span);
      return true;
    }
  };

  @Test public void spanReporter_getsLocalEndpointInfo() {
    String expectedLocalServiceName = "favistar", expectedLocalIp = "1.2.3.4";
    int expectedLocalPort = 80;

    List<Span> zipkinSpans = new ArrayList<>();
    Reporter<Span> spanReporter = span -> {
      assertThat(span.localServiceName()).isEqualTo(expectedLocalServiceName);
      assertThat(span.localEndpoint().ipv4()).isEqualTo(expectedLocalIp);
      assertThat(span.localEndpoint().portAsInt()).isEqualTo(expectedLocalPort);
      zipkinSpans.add(span);
    };

    try (Tracing tracing = Tracing.newBuilder()
        .localServiceName(expectedLocalServiceName)
        .localIp(expectedLocalIp)
        .localPort(expectedLocalPort)
        .spanReporter(spanReporter)
        .build()) {
      tracing.tracer().newTrace().start().finish();
    }

    assertThat(zipkinSpans).isNotEmpty(); // ensures the assertions passed.
  }

  @Test public void firehose_dataChangesVisibleToZipkin() {
    String serviceNameOverride = "favistar";

    FinishedSpanHandler finishedSpanHandler = new FinishedSpanHandler() {
      @Override public boolean handle(TraceContext context, MutableSpan span) {
        span.localServiceName(serviceNameOverride);
        return true;
      }
    };

    try (Tracing tracing = Tracing.newBuilder()
        .spanReporter(spans::add)
        .addFinishedSpanHandler(finishedSpanHandler)
        .build()) {
      tracing.tracer().newTrace().start().finish();
    }

    assertThat(spans.get(0).localServiceName()).isEqualTo(serviceNameOverride);
  }

  @Test public void firehose_recordsWhenSampled() {
    try (Tracing tracing = Tracing.newBuilder()
        .spanReporter(spans::add)
        .addFinishedSpanHandler(finishedSpanHandler)
        .build()) {
      tracing.tracer().newTrace().start().name("aloha").finish();
    }

    assertThat(spans).hasSameSizeAs(mutableSpans).hasSize(1);
    assertThat(spans.get(0).name()).isEqualTo(mutableSpans.get(0).name());
    assertThat(spans.get(0).timestampAsLong()).isEqualTo(mutableSpans.get(0).startTimestamp());
    long mutableSpanDuration =
        Math.max(1, mutableSpans.get(0).finishTimestamp() - mutableSpans.get(0).startTimestamp());
    assertThat(spans.get(0).durationAsLong()).isEqualTo(mutableSpanDuration);
  }

  @Test public void firehose_doesntRecordWhenUnsampled() {
    try (Tracing tracing = Tracing.newBuilder()
        .spanReporter(spans::add)
        .addFinishedSpanHandler(finishedSpanHandler)
        .sampler(Sampler.NEVER_SAMPLE)
        .build()) {
      tracing.tracer().newTrace().start().name("aloha").finish();
    }

    assertThat(spans).isEmpty();
    assertThat(mutableSpans).isEmpty();
  }

  @Test public void firehose_recordsWhenReporterIsNoopIfAlwaysSampleLocal() {
    try (Tracing tracing = Tracing.newBuilder()
        .spanReporter(Reporter.NOOP)
        .addFinishedSpanHandler(finishedSpanHandler)
        .build()) {
      tracing.tracer().newTrace().start().name("aloha").finish();
    }

    assertThat(spans).isEmpty();
    assertThat(mutableSpans).hasSize(1);
  }

  @Test public void firehose_recordsWhenUnsampledIfAlwaysSampleLocal() {
    try (Tracing tracing = Tracing.newBuilder()
        .spanReporter(spans::add)
        .addFinishedSpanHandler(new FinishedSpanHandler() {
          @Override public boolean handle(TraceContext context, MutableSpan span) {
            mutableSpans.add(span);
            return true;
          }

          @Override public boolean alwaysSampleLocal() {
            return true;
          }
        })
        .sampler(Sampler.NEVER_SAMPLE)
        .build()) {
      tracing.tracer().newTrace().start().name("aloha").finish();
    }

    assertThat(spans).isEmpty();
    assertThat(mutableSpans).hasSize(1);
  }

  @Test public void firehose_recordsWhenUnsampledIfContextSamplesLocal() {
    AtomicBoolean sampledLocal = new AtomicBoolean();
    try (Tracing tracing = Tracing.newBuilder()
        .spanReporter(spans::add)
        .propagationFactory(new Propagation.Factory() {
          @Override public <K> Propagation<K> create(Propagation.KeyFactory<K> keyFactory) {
            return B3SinglePropagation.FACTORY.create(keyFactory);
          }

          @Override public TraceContext decorate(TraceContext context) {
            if (sampledLocal.getAndSet(true)) return context;
            return context.toBuilder().sampledLocal(true).build();
          }
        })
        .addFinishedSpanHandler(finishedSpanHandler)
        .sampler(Sampler.NEVER_SAMPLE)
        .build()) {
      tracing.tracer().newTrace().start().name("one").finish();
      tracing.tracer().newTrace().start().name("two").finish();
    }

    assertThat(spans).isEmpty();
    assertThat(mutableSpans).hasSize(1);
    assertThat(mutableSpans.get(0).name()).isEqualTo("one");
  }
}

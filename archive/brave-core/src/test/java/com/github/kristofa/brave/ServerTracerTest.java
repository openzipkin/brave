package com.github.kristofa.brave;

import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import zipkin2.Span.Kind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNull;

public class ServerTracerTest {

  private static final long START_TIME_MICROSECONDS = System.currentTimeMillis() * 1000;
  private static final long TRACE_ID = 1;
  private static final SpanId CONTEXT =
      SpanId.builder().sampled(true).traceId(TRACE_ID).parentId(2L).spanId(3).build();
  private static final Endpoint ENDPOINT = Endpoint.create("service", 127 << 24 | 1);
  static final zipkin2.Endpoint ZIPKIN_ENDPOINT = zipkin2.Endpoint.newBuilder()
      .serviceName("service").ip("127.0.0.1").build();
  private static final zipkin2.Span BASE_SPAN = zipkin2.Span.newBuilder()
      .traceId(String.format("%016x", TRACE_ID))
      .parentId(String.format("%016x", 2))
      .id(String.format("%016x", 3)).build();
  private static final String SPAN_NAME = "span name";

  long timestamp = START_TIME_MICROSECONDS;
  AnnotationSubmitter.Clock clock = () -> timestamp;

  private Span span = Brave.toSpan(CONTEXT);
  ServerSpan serverSpan = new AutoValue_ServerSpan(CONTEXT, span, true);

  List<zipkin2.Span> spans = new ArrayList<>();
  Brave brave = newBrave(true);
  Recorder recorder = brave.serverTracer().recorder();

  @Before
  public void setup() {
    ThreadLocalServerClientAndLocalSpanState.clear();
  }

  Brave newBrave(boolean supportsJoin) {
    return new Brave.Builder(ENDPOINT)
        .clock(clock)
        .spanReporter(spans::add)
        .supportsJoin(false)
        .build();
  }

  @Test
  public void clearCurrentSpan() {
    brave.serverTracer().clearCurrentSpan();
    assertThat(brave.serverTracer().currentSpan().get()).isNull();
  }

  @Test
  public void setStateCurrentTrace() {
    brave.serverTracer().setStateCurrentTrace(CONTEXT, SPAN_NAME);

    recorder.flush(brave.serverSpanThreadBinder().get());
    assertThat(spans.get(0).name()).isEqualTo(SPAN_NAME);
  }

  @Test
  public void setStateCurrentTrace_joinUnsupported() {
    brave = newBrave(false);
    recorder = brave.serverTracer().recorder();

    brave.serverTracer().setStateCurrentTrace(CONTEXT, SPAN_NAME);

    recorder.flush(brave.serverSpanThreadBinder().get());
    assertThat(spans.get(0).parentId()).isEqualTo(BASE_SPAN.id());
  }

  @Test
  public void setStateNoTracing() {
    brave.serverTracer().setStateNoTracing();

    assertThat(brave.serverSpanThreadBinder().sampled())
        .isFalse();
  }

  @Test
  public void setStateUnknown_sampled() {
    brave.serverTracer().setStateUnknown(SPAN_NAME);

    assertThat(brave.serverSpanThreadBinder().sampled())
        .isTrue();
  }

  @Test
  public void setStateUnknown_setsName() {
    brave.serverTracer().setStateUnknown(SPAN_NAME);

    recorder.flush(brave.serverSpanThreadBinder().get());
    assertThat(spans.get(0).name()).isEqualTo(SPAN_NAME);
  }

  @Test
  public void startNewSpan_unsampledBrave() {
    Brave brave = new Brave.Builder(ENDPOINT).traceSampler(Sampler.NEVER_SAMPLE).build();

    brave.serverTracer().setStateUnknown(SPAN_NAME);
    assertNull(brave.serverSpanThreadBinder().get());
  }

  @Test
  public void setServerReceived_noopWhenNoCurrentSpan() {
    brave.serverTracer().setServerReceived();

    assertThat(spans).isEmpty();
    recorder.flush(span);
    assertThat(spans).matches(s -> s.isEmpty() || s.contains(BASE_SPAN));
  }

  @Test
  public void setServerReceived_doesntFlush() {
    brave.serverSpanThreadBinder().setCurrentSpan(serverSpan);
    brave.serverTracer().setServerReceived();

    assertThat(spans).matches(s -> s.isEmpty() || s.contains(BASE_SPAN));
  }

  @Test
  public void setServerReceived() {
    brave.serverSpanThreadBinder().setCurrentSpan(serverSpan);
    brave.serverTracer().setServerReceived();

    recorder.flush(span);

    assertThat(spans.get(0)).isEqualTo(
        BASE_SPAN.toBuilder()
            .kind(Kind.SERVER)
            .localEndpoint(ZIPKIN_ENDPOINT)
            .timestamp(START_TIME_MICROSECONDS).build()
    );
  }

  @Test
  public void setServerReceived_clientAddress() {
    brave.serverSpanThreadBinder().setCurrentSpan(serverSpan);
    brave.serverTracer().setServerReceived(Endpoint.builder()
        .ipv4(127 << 24 | 1).port(9).serviceName("foobar").build());

    recorder.flush(span);

    assertThat(spans.get(0).remoteEndpoint()).isEqualTo(
        zipkin2.Endpoint.newBuilder().serviceName("foobar").ip("127.0.0.1").port(9).build()
    );
  }

  @Test
  public void setServerReceived_clientAddress_nullName() {
    brave.serverSpanThreadBinder().setCurrentSpan(serverSpan);

    brave.serverTracer()
        .setServerReceived(1 << 24 | 2 << 16 | 3 << 8 | 4, 9999, null);

    recorder.flush(span);
    assertThat(spans.get(0).remoteServiceName())
        .isEqualTo("unknown");
  }

  @Test
  public void setServerSend_noopWhenNoCurrentSpan() {
    brave.serverTracer().setServerSend();

    assertThat(spans).isEmpty();
    assertThat(brave.serverSpanThreadBinder().get()).isNull();
  }

  @Test
  public void setServerSend() {
    recorder.start(span, START_TIME_MICROSECONDS);
    brave.serverSpanThreadBinder().setCurrentSpan(serverSpan);

    timestamp = START_TIME_MICROSECONDS + 100;

    brave.serverTracer().setServerSend();

    assertThat(spans.get(0)).isEqualTo(
        BASE_SPAN.toBuilder()
            .kind(Kind.SERVER)
            .localEndpoint(ZIPKIN_ENDPOINT)
            .timestamp(START_TIME_MICROSECONDS).duration(100L).build()
    );
  }

  @Test
  public void setServerSend_preciseDuration() {
    recorder.start(span, START_TIME_MICROSECONDS);
    brave.serverSpanThreadBinder().setCurrentSpan(serverSpan);

    timestamp = START_TIME_MICROSECONDS + 500;

    brave.serverTracer().setServerSend();

    assertThat(spans.get(0).duration()).isEqualTo(500L);
  }

  /** Duration of less than one microsecond is confusing to plot and could coerce to null. */
  @Test
  public void setServerSend_lessThanMicrosRoundUp() {
    recorder.start(span, START_TIME_MICROSECONDS);
    brave.serverSpanThreadBinder().setCurrentSpan(serverSpan);

    timestamp = START_TIME_MICROSECONDS; // no time passed!

    brave.serverTracer().setServerSend();

    assertThat(spans.get(0).duration()).isEqualTo(1L);
  }

  @Test
  public void setServerSend_skipsDurationWhenNoTimestamp() {
    // duration unset due to client-originated trace
    brave.serverSpanThreadBinder().setCurrentSpan(serverSpan);

    timestamp = START_TIME_MICROSECONDS + 1L;

    brave.serverTracer().setServerSend();

    assertThat(spans.get(0).duration()).isNull();
  }
}

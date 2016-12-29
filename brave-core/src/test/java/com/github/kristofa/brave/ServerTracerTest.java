package com.github.kristofa.brave;

import com.github.kristofa.brave.internal.DefaultSpanCodec;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import zipkin.Constants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNull;

@RunWith(PowerMockRunner.class)
// Added to declutter console: tells power mock not to mess with implicit classes we aren't testing
@PowerMockIgnore({"org.apache.logging.*", "javax.script.*"})
@PrepareForTest(AnnotationSubmitter.DefaultClock.class)
public class ServerTracerTest {

  private static final long START_TIME_MICROSECONDS = System.currentTimeMillis() * 1000;
  private static final long TRACE_ID = 1;
  private static final SpanId CONTEXT =
      SpanId.builder().sampled(true).traceId(TRACE_ID).spanId(2).parentId(3L).build();
  private static final Endpoint ENDPOINT = Endpoint.create("serviceName", 80);
  private static final zipkin.Endpoint ZIPKIN_ENDPOINT = zipkin.Endpoint.create("serviceName", 80);
  private static final zipkin.Span BASE_SPAN = DefaultSpanCodec.toZipkin(Brave.newSpan(CONTEXT));
  private static final String SPAN_NAME = "span name";

  private Span span = Brave.newSpan(CONTEXT);
  ServerSpan serverSpan = new AutoValue_ServerSpan(CONTEXT, span, true);

  List<zipkin.Span> spans = new ArrayList<>();
  Brave brave;
  Recorder recorder;

  @Before
  public void setup() {
    ThreadLocalServerClientAndLocalSpanState.clear();

    PowerMockito.mockStatic(System.class);
    PowerMockito.when(System.currentTimeMillis()).thenReturn(START_TIME_MICROSECONDS / 1000);
    PowerMockito.when(System.nanoTime()).thenReturn(0L);

    // deferred so that mockito can work on the clock!
    brave = new Brave.Builder(ENDPOINT)
        .clock(new AnnotationSubmitter.DefaultClock())
        .reporter(spans::add).build();
    recorder = brave.serverTracer().recorder();
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
    assertThat(spans.get(0).name).isEqualTo(SPAN_NAME);
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
    assertThat(spans.get(0).name).isEqualTo(SPAN_NAME);
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

  public void setServerReceived_doesntFlush() {
    brave.serverSpanThreadBinder().setCurrentSpan(serverSpan);
    brave.serverTracer().setServerReceived();

    assertThat(spans).containsExactly(BASE_SPAN);
  }

  @Test
  public void setServerReceived() {
    brave.serverSpanThreadBinder().setCurrentSpan(serverSpan);
    brave.serverTracer().setServerReceived();

    recorder.flush(span);
    assertThat(spans.get(0).timestamp).isEqualTo(START_TIME_MICROSECONDS);
    assertThat(spans.get(0).annotations).containsExactly(
        zipkin.Annotation.create(START_TIME_MICROSECONDS,
            Constants.SERVER_RECV,
            ZIPKIN_ENDPOINT
        )
    );
  }

  @Test
  public void setServerReceived_clientAddress() {
    brave.serverSpanThreadBinder().setCurrentSpan(serverSpan);
    brave.serverTracer().setServerReceived(Endpoint.builder()
        .ipv4(127 << 24 | 1).port(9).serviceName("foobar").build());

    recorder.flush(span);
    assertThat(spans.get(0).binaryAnnotations).containsExactly(
        zipkin.BinaryAnnotation.address(
            Constants.CLIENT_ADDR,
            zipkin.Endpoint.builder().serviceName("foobar").ipv4(127 << 24 | 1).port(9).build()
        )
    );
  }

  @Test
  public void setServerReceived_clientAddress_nullName() {
    brave.serverSpanThreadBinder().setCurrentSpan(serverSpan);

    brave.serverTracer()
        .setServerReceived(1 << 24 | 2 << 16 | 3 << 8 | 4, 9999, null);

    recorder.flush(span);
    assertThat(spans.get(0).binaryAnnotations.get(0).endpoint.serviceName)
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
    recorder.start(span, 100L);
    brave.serverSpanThreadBinder().setCurrentSpan(serverSpan);

    brave.serverTracer().setServerSend();

    assertThat(spans.get(0).duration).isEqualTo(START_TIME_MICROSECONDS - 100L);
    assertThat(spans.get(0).annotations).contains(
        zipkin.Annotation.create(START_TIME_MICROSECONDS,
            Constants.SERVER_SEND,
            ZIPKIN_ENDPOINT
        )
    );
  }

  @Test
  public void setServerSend_preciseDuration() {
    recorder.start(span, START_TIME_MICROSECONDS);
    brave.serverSpanThreadBinder().setCurrentSpan(serverSpan);

    PowerMockito.when(System.nanoTime()).thenReturn(500000L);

    brave.serverTracer().setServerSend();

    assertThat(spans.get(0).duration).isEqualTo(500L);
  }

  /** Duration of less than one microsecond is confusing to plot and could coerce to null. */
  @Test
  public void setServerSend_lessThanMicrosRoundUp() {
    recorder.start(span, START_TIME_MICROSECONDS);
    brave.serverSpanThreadBinder().setCurrentSpan(serverSpan);

    PowerMockito.when(System.nanoTime()).thenReturn(500L);

    brave.serverTracer().setServerSend();

    assertThat(spans.get(0).duration).isEqualTo(1L);
  }

  @Test
  public void setServerSend_skipsDurationWhenNoTimestamp() {
    // duration unset due to client-originated trace
    brave.serverSpanThreadBinder().setCurrentSpan(serverSpan);

    PowerMockito.when(System.nanoTime()).thenReturn(500L);

    brave.serverTracer().setServerSend();

    assertThat(spans.get(0).duration).isNull();
  }
}

package com.github.kristofa.brave;

import com.github.kristofa.brave.internal.DefaultSpanCodec;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import zipkin.Constants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ClientTracerTest {

  private static final long START_TIME_MICROSECONDS = System.currentTimeMillis() * 1000;
  private static final String REQUEST_NAME = "requestname";
  private static final long TRACE_ID = 105;
  private static final SpanId PARENT_CONTEXT =
      SpanId.builder().traceId(TRACE_ID).spanId(103).build();
  private static final Endpoint ENDPOINT = Endpoint.create("serviceName", 80);
  private static final zipkin.Endpoint ZIPKIN_ENDPOINT = zipkin.Endpoint.create("serviceName", 80);
  private static final zipkin.Span BASE_SPAN =
      DefaultSpanCodec.toZipkin(Brave.toSpan(SpanId.builder().spanId(TRACE_ID).build()));

  long timestamp = START_TIME_MICROSECONDS;
  AnnotationSubmitter.Clock clock = () -> timestamp;

  private Span span = Brave.toSpan(SpanId.builder().spanId(TRACE_ID).sampled(true).build());

  List<zipkin.Span> spans = new ArrayList<>();
  Brave brave = newBrave();
  Recorder recorder = brave.clientTracer().recorder();

  @Before
  public void setup() {
    ThreadLocalServerClientAndLocalSpanState.clear();
  }

  Brave newBrave() {
    return new Brave.Builder(ENDPOINT).clock(clock).reporter(spans::add).build();
  }

  @Test
  public void setClientSent_noopWhenNoCurrentSpan() {
    brave.clientTracer().setClientSent();

    assertThat(spans).isEmpty();
    recorder.flush(span);
    assertThat(spans).matches(s -> s.isEmpty() || s.contains(BASE_SPAN));
  }

  public void setClientSent_doesntFlush() {
    brave.clientSpanThreadBinder().setCurrentSpan(span);
    brave.clientTracer().setClientSent();

    assertThat(spans).matches(s -> s.isEmpty() || s.contains(BASE_SPAN));
  }

  @Test
  public void setClientSent() {
    brave.clientSpanThreadBinder().setCurrentSpan(span);
    brave.clientTracer().setClientSent();

    recorder.flush(brave.clientSpanThreadBinder().get());
    assertThat(spans.get(0).timestamp).isEqualTo(START_TIME_MICROSECONDS);
    assertThat(spans.get(0).annotations).containsExactly(
        zipkin.Annotation.create(START_TIME_MICROSECONDS,
            Constants.CLIENT_SEND,
            ZIPKIN_ENDPOINT
        )
    );
  }

  @Test
  public void setClientSent_serverAddress() {
    brave.clientSpanThreadBinder().setCurrentSpan(span);
    brave.clientTracer().setClientSent(Endpoint.builder()
        .ipv4(127 << 24 | 1).port(9).serviceName("foobar").build());

    recorder.flush(span);
    assertThat(spans.get(0).binaryAnnotations).containsExactly(
        zipkin.BinaryAnnotation.address(
            Constants.SERVER_ADDR,
            zipkin.Endpoint.builder().serviceName("foobar").ipv4(127 << 24 | 1).port(9).build()
        )
    );
  }

  @Test
  public void setClientSent_serverAddress_nullName() {
    brave.clientSpanThreadBinder().setCurrentSpan(span);

    brave.clientTracer()
        .setClientSent(1 << 24 | 2 << 16 | 3 << 8 | 4, 9999, null);

    recorder.flush(span);
    assertThat(spans.get(0).binaryAnnotations.get(0).endpoint.serviceName)
        .isEqualTo("unknown");
  }

  @Test
  public void startNewSpan_unsampledServerSpan() {
    brave.serverSpanThreadBinder().setCurrentSpan(ServerSpan.NOT_SAMPLED);

    assertNull(brave.clientTracer().startNewSpan(REQUEST_NAME));
    assertNull(brave.clientSpanThreadBinder().get());
  }

  @Test
  public void startNewSpan_unsampledBrave() {
    Brave brave = new Brave.Builder(ENDPOINT).traceSampler(Sampler.NEVER_SAMPLE).build();

    assertNull(brave.clientTracer().startNewSpan(REQUEST_NAME));
    assertNull(brave.clientSpanThreadBinder().get());
  }

  @Test
  public void startNewSpan_createsNewTraceAndAttachesCurrentSpan() {
    brave.serverSpanThreadBinder().setCurrentSpan(ServerSpan.EMPTY);

    SpanId newContext = brave.clientTracer().startNewSpan(REQUEST_NAME);
    assertNotNull(newContext);
    assertNull(newContext.nullableParentId());
    assertThat(Brave.context(brave.clientSpanThreadBinder().get()))
        .isEqualTo(newContext);
  }

  @Test
  public void startNewSpan_createsChild() {
    final ServerSpan parentSpan = ServerSpan.create(Brave.toSpan(PARENT_CONTEXT));
    brave.serverSpanThreadBinder().setCurrentSpan(parentSpan);

    SpanId newContext = brave.clientTracer().startNewSpan(REQUEST_NAME);
    assertNotNull(newContext);
    assertEquals(TRACE_ID, newContext.traceId);
    assertEquals(PARENT_CONTEXT.spanId, newContext.parentId);
    assertThat(Brave.context(brave.clientSpanThreadBinder().get()))
        .isEqualTo(newContext);
  }

  @Test
  public void startNewSpan_setsRequestName() {
    brave.clientTracer().startNewSpan(REQUEST_NAME);

    recorder.flush(brave.clientSpanThreadBinder().get());
    assertThat(spans.get(0).name).isEqualTo(REQUEST_NAME);
  }

  @Test
  public void setClientReceived_noopWhenNoCurrentSpan() {
    brave.clientTracer().setClientReceived();

    assertThat(spans).isEmpty();
    assertThat(brave.clientSpanThreadBinder().get()).isNull();
  }

  @Test
  public void setClientReceived() {
    recorder.start(span, START_TIME_MICROSECONDS);
    brave.clientSpanThreadBinder().setCurrentSpan(span);

    timestamp = START_TIME_MICROSECONDS + 100;

    brave.clientTracer().setClientReceived();

    assertThat(spans.get(0).duration).isEqualTo(100L);
    assertThat(spans.get(0).annotations).contains(
        zipkin.Annotation.create(START_TIME_MICROSECONDS + 100,
            Constants.CLIENT_RECV,
            ZIPKIN_ENDPOINT
        )
    );
  }

  @Test
  public void setClientReceived_preciseDuration() {
    recorder.start(span, START_TIME_MICROSECONDS);
    brave.clientSpanThreadBinder().setCurrentSpan(span);

    timestamp = START_TIME_MICROSECONDS + 500;

    brave.clientTracer().setClientReceived();

    assertThat(spans.get(0).duration).isEqualTo(500L);
  }

  /** Duration of less than one microsecond is confusing to plot and could coerce to null. */
  @Test
  public void setClientReceived_lessThanMicrosRoundUp() {
    recorder.start(span, START_TIME_MICROSECONDS);
    brave.clientSpanThreadBinder().setCurrentSpan(span);

    timestamp = START_TIME_MICROSECONDS; // no time passed!

    brave.clientTracer().setClientReceived();

    assertThat(spans.get(0).duration).isEqualTo(1L);
  }
}

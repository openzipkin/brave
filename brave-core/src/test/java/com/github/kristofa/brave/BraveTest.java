package com.github.kristofa.brave;

import com.github.kristofa.brave.AnnotationSubmitter.Clock;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import java.net.NetworkInterface;
import java.net.SocketException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
// Added to declutter console: tells power mock not to mess with implicit classes we aren't testing
@PowerMockIgnore({"org.apache.logging.*", "javax.script.*"})
@PrepareForTest({InetAddressUtilities.class, NetworkInterface.class})
public class BraveTest {
  Brave brave = newBrave();

  @Before
  public void setup() {
    ThreadLocalServerClientAndLocalSpanState.clear();
  }

  protected Brave newBrave() {
    return new Brave.Builder().build();
  }

  protected Brave newBrave(Sampler sampler) {
    return new Brave.Builder().traceSampler(sampler).build();
  }

  protected Brave newBraveWith128BitTraceIds() {
    return new Brave.Builder().traceId128Bit(true).build();
  }

  @Test
  public void testGetClock() {
    Clock clock = brave.clock();
    assertNotNull(clock);
    assertSame(clock, brave.clock());
  }

  @Test
  public void testGetClientTracer() {
    final ClientTracer clientTracer = brave.clientTracer();
    assertNotNull(clientTracer);

    final ClientTracer secondClientTracer =
        brave.clientTracer();
    assertSame("It is important that each client tracer we get shares same state.",
        clientTracer.currentSpan(), secondClientTracer.currentSpan());
  }

  @Test
  public void testGetServerTracer() {
    final ServerTracer serverTracer = brave.serverTracer();
    assertNotNull(serverTracer);

    final ServerTracer secondServerTracer = brave.serverTracer();
    assertSame("It is important that each server tracer we get shares same state.",
        serverTracer.currentSpan(), secondServerTracer.currentSpan());
  }

  @Test
  public void testStateBetweenServerAndClient() {
    final ClientTracer clientTracer =
        brave.clientTracer();
    final ServerTracer serverTracer =
        brave.serverTracer();

    assertSame("Client and server tracers should share same state.",
        clientTracer.currentServerSpan(),
        serverTracer.currentSpan());
  }

  @Test
  public void testGetLocalTracer() {
    final LocalTracer localTracer = brave.localTracer();
    assertNotNull(localTracer);

    final LocalTracer secondLocalTracer =
        brave.localTracer();
    assertSame("It is important that each local tracer we get shares same state.",
        localTracer.currentSpan(), secondLocalTracer.currentSpan());
  }

  @Test
  public void testGetServerSpanAnnotationSubmitter() {
    assertNotNull(brave.serverSpanAnnotationSubmitter());
  }

  @Test
  public void newSpan_with64bitTraceId() {
    Span span = brave.serverTracer().spanFactory().nextSpan(null);
    assertThat(span.getTrace_id_high()).isZero();
    assertThat(span.getTrace_id()).isNotZero();
  }

  @Test
  public void newSpan_whenUnsampled() {
    Sampler sampler = Sampler.NEVER_SAMPLE;
    brave = newBrave(sampler);

    Span span = brave.serverTracer().spanFactory().nextSpan(null);
    assertThat(Brave.context(span).sampled()).isFalse();
  }

  @Test
  public void newSpan_whenParentHas128bitTraceId() {
    SpanId parentSpan = SpanId.builder().traceIdHigh(3).traceId(2).spanId(1).build();

    Span span = brave.serverTracer().spanFactory().nextSpan(parentSpan);
    assertThat(span.getTrace_id_high())
        .isEqualTo(parentSpan.traceIdHigh);
    assertThat(span.getTrace_id())
        .isEqualTo(parentSpan.traceId);
    assertThat(span.getParent_id())
        .isEqualTo(parentSpan.spanId);
  }

  @Test
  public void newSpan_rootSpanWith128bitTraceId() {
    brave = newBraveWith128BitTraceIds();

    Span span = brave.serverTracer().spanFactory().nextSpan(null);
    assertThat(span.getTrace_id_high()).isNotZero();
  }

  @Test
  public void testGetServerSpanThreadBinder() {
    assertNotNull(brave.serverSpanThreadBinder());
  }

  @Test public void implicitLocalEndpoint_exceptionReadingNics() throws Exception {
    mockStatic(NetworkInterface.class);
    when(NetworkInterface.getNetworkInterfaces()).thenThrow(SocketException.class);

    assertThat(new Brave.Builder().localEndpoint)
        .isEqualTo(Endpoint.create("unknown", 127 << 24 | 1));
  }
}

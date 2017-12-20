package com.github.kristofa.brave.internal;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.ThreadLocalServerClientAndLocalSpanState;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InternalTest {
  static {
    InternalSpan.initializeInstanceForTests();
  }

  SpanId context = SpanId.builder().spanId(1L).build();
  Span span = InternalSpan.instance.toSpan(context);

  List<zipkin2.Span> spans = new ArrayList<>();
  Brave brave = new Brave.Builder().spanReporter(spans::add).build();

  @Before
  public void setup() {
    ThreadLocalServerClientAndLocalSpanState.clear();
  }

  @Test
  public void addsClientAddress() {
    brave.serverTracer().setStateCurrentTrace(context, "foo");

    Endpoint ca = Endpoint.create("foo", 127 << 24 | 1);
    Internal.instance.setClientAddress(brave, ca);

    brave.serverTracer().setServerSend(); // flush

    assertThat(spans.get(0).remoteEndpoint()).isEqualTo(
        zipkin2.Endpoint.newBuilder().serviceName("foo").ip("127.0.0.1").build()
    );
  }
}

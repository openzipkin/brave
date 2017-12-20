package com.github.kristofa.brave;

import brave.Tracing;
import org.junit.After;

public class Brave4ServerRequestInterceptorTest extends ServerRequestInterceptorTest {
  @Override Brave newBrave() {
    return TracerAdapter.newBrave(Tracing.newBuilder()
        .localEndpoint(ZIPKIN_ENDPOINT)
        .spanReporter(spans::add).build().tracer());
  }

  @After public void close(){
    Tracing.current().close();
  }
}

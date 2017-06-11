package com.github.kristofa.brave;

import brave.Tracing;

public class Brave4ServerRequestInterceptorTest extends ServerRequestInterceptorTest {
  @Override Brave newBrave() {
    return TracerAdapter.newBrave(Tracing.newBuilder()
        .localEndpoint(ZIPKIN_ENDPOINT)
        .reporter(spans::add).build().tracer());
  }
}

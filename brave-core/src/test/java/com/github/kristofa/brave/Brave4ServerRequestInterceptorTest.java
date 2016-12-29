package com.github.kristofa.brave;

import brave.Tracer;

public class Brave4ServerRequestInterceptorTest extends ServerRequestInterceptorTest {
  @Override Brave newBrave() {
    return TracerAdapter.newBrave(Tracer.newBuilder()
        .localEndpoint(ZIPKIN_ENDPOINT)
        .reporter(spans::add).build());
  }
}

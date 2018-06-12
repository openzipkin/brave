package com.github.kristofa.brave;

import brave.Tracing;
import org.junit.After;

public class Brave4LocalTracerTest extends LocalTracerTest {
  @Override Brave newBrave() {
    return TracerAdapter.newBrave(Tracing.newBuilder()
        .clock(() -> timestamp)
        .endpoint(ZIPKIN_ENDPOINT)
        .spanReporter(spans::add).build());
  }

  @Override Brave newBrave(ServerClientAndLocalSpanState state) {
    return TracerAdapter.newBrave(Tracing.newBuilder()
        .clock(() -> timestamp)
        .endpoint(ZIPKIN_ENDPOINT)
        .spanReporter(spans::add).build(), state);
  }

  @After public void close(){
    Tracing.current().close();
  }
}

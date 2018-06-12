package com.github.kristofa.brave;

import brave.Tracing;
import org.junit.After;

public class Brave4ServerTracerTest extends ServerTracerTest {
  @Override Brave newBrave(boolean supportsJoin) {
    return TracerAdapter.newBrave(Tracing.newBuilder()
        .clock(clock::currentTimeMicroseconds)
        .endpoint(ZIPKIN_ENDPOINT)
        .spanReporter(spans::add)
        .supportsJoin(supportsJoin)
        .build());
  }

  @After public void close(){
    Tracing.current().close();
  }
}

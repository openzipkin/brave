package com.github.kristofa.brave;

import brave.Tracing;
import org.junit.After;

public class Brave4ClientTracerTest extends ClientTracerTest {
  @Override Brave newBrave() {
    return TracerAdapter.newBrave(Tracing.newBuilder()
        .clock(new AnnotationSubmitter.DefaultClock()::currentTimeMicroseconds)
        .endpoint(ZIPKIN_ENDPOINT)
        .clock(clock::currentTimeMicroseconds)
        .spanReporter(spans::add).build());
  }

  @After public void close(){
    Tracing.current().close();
  }
}

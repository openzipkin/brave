package com.github.kristofa.brave;

import brave.Tracer;

public class Brave4Test extends BraveTest {

  @Override protected Brave newBrave() {
    return TracerAdapter.newBrave(Tracer.newBuilder().build());
  }

  @Override protected Brave newBrave(Sampler sampler) {
    return TracerAdapter.newBrave(Tracer.newBuilder().sampler(new brave.sampler.Sampler() {
      @Override public boolean isSampled(long traceId) {
        return sampler.isSampled(traceId);
      }
    }).build());
  }

  @Override protected Brave newBraveWith128BitTraceIds() {
    return TracerAdapter.newBrave(Tracer.newBuilder().traceId128Bit(true).build());
  }
}

package com.github.kristofa.brave;

import brave.Tracing;

public class Brave4Test extends BraveTest {

  @Override protected Brave newBrave() {
    return TracerAdapter.newBrave(Tracing.newBuilder().build().tracer());
  }

  @Override protected Brave newBrave(Sampler sampler) {
    return TracerAdapter.newBrave(Tracing.newBuilder().sampler(new brave.sampler.Sampler() {
      @Override public boolean isSampled(long traceId) {
        return sampler.isSampled(traceId);
      }
    }).build().tracer());
  }

  @Override protected Brave newBraveWith128BitTraceIds() {
    return TracerAdapter.newBrave(Tracing.newBuilder().traceId128Bit(true).build().tracer());
  }
}

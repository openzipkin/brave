package com.github.kristofa.brave;

import org.assertj.core.data.Percentage;

import static org.assertj.core.data.Percentage.withPercentage;

public class BoundarySamplerTest extends SamplerTest {
  @Override Sampler newSampler(float rate) {
    return BoundarySampler.create(rate);
  }

  @Override Percentage expectedErrorRate() {
    return withPercentage(10);
  }
}

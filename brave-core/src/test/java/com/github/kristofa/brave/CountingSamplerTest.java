package com.github.kristofa.brave;

import org.assertj.core.data.Percentage;

import static org.assertj.core.data.Percentage.withPercentage;

public class CountingSamplerTest extends SamplerTest {
  @Override Sampler newSampler(float rate) {
    return CountingSampler.create(rate);
  }

  @Override Percentage expectedErrorRate() {
    return withPercentage(0);
  }
}

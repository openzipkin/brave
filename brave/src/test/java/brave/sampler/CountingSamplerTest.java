package brave.sampler;

import org.assertj.core.data.Percentage;
import org.junit.Test;

import static org.assertj.core.data.Percentage.withPercentage;

public class CountingSamplerTest extends SamplerTest {
  @Override Sampler newSampler(float rate) {
    return CountingSampler.create(rate);
  }

  @Override Percentage expectedErrorRate() {
    return withPercentage(0);
  }

  @Test
  public void sampleRateMinimumOnePercent() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    newSampler(0.0001f);
  }
}

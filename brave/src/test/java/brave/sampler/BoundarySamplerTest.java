package brave.sampler;

import org.assertj.core.data.Percentage;
import org.junit.Test;

import static org.assertj.core.data.Percentage.withPercentage;

public class BoundarySamplerTest extends SamplerTest {
  @Override Sampler newSampler(float rate) {
    return BoundarySampler.create(rate);
  }

  @Override Percentage expectedErrorRate() {
    return withPercentage(10);
  }

  @Test
  public void acceptsOneInTenThousandSampleRate() {
    newSampler(0.0001f);
  }
}

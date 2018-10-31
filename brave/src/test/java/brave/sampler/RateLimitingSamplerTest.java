package brave.sampler;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RateLimitingSamplerTest {

  private Sampler sampler;

  @Before public void setup() {
    sampler = RateLimitingSampler.create(1);
  }

  @Test public void samplesOnlySpecifiedNumber() {
    List<Boolean> sampleDecisions = Arrays.asList(
        sampler.isSampled(0),
        sampler.isSampled(1),
        sampler.isSampled(2),
        sampler.isSampled(3),
        sampler.isSampled(4));

    // Sampled
    assertThat(sampleDecisions.stream().filter(d -> d).count()).isEqualTo(1);
    // Not Sampled
    assertThat(sampleDecisions.stream().filter(d -> !d).count()).isEqualTo(4);
  }

  @Test public void resetsAfterASecond() throws InterruptedException {
    List<Boolean> sampleDecisions = new ArrayList<>(Arrays.asList(
        sampler.isSampled(0),
        sampler.isSampled(1),
        sampler.isSampled(2),
        sampler.isSampled(3),
        sampler.isSampled(4)));

    Thread.sleep(Duration.of(1, ChronoUnit.SECONDS).plusMillis(1).toMillis());

    sampleDecisions.addAll(Arrays.asList(
        sampler.isSampled(5),
        sampler.isSampled(6),
        sampler.isSampled(7),
        sampler.isSampled(8),
        sampler.isSampled(9)));

    // Sampled
    assertThat(sampleDecisions.stream().filter(d -> d).count()).isEqualTo(2);
    // Not Sampled
    assertThat(sampleDecisions.stream().filter(d -> !d).count()).isEqualTo(8);
  }

  @Test public void specifiedNumberWithLotsOfTraces() {
    long passed = new Random().longs(100000).parallel().filter(sampler::isSampled).count();

    assertThat(passed).isEqualTo(1);
  }
}

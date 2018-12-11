package brave.sampler;

import java.util.Random;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static brave.sampler.RateLimitingSampler.NANOS_PER_DECISECOND;
import static brave.sampler.RateLimitingSampler.NANOS_PER_SECOND;
import static brave.sampler.SamplerTest.INPUT_SIZE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
// Added to declutter console: tells power mock not to mess with implicit classes we aren't testing
@PowerMockIgnore({"org.apache.logging.*", "javax.script.*"})
@PrepareForTest(RateLimitingSampler.class)
public class RateLimitingSamplerTest {

  @Test public void samplesOnlySpecifiedNumber() {
    mockStatic(System.class);
    when(System.nanoTime()).thenReturn(NANOS_PER_SECOND);
    Sampler sampler = RateLimitingSampler.create(2);

    when(System.nanoTime()).thenReturn(NANOS_PER_SECOND + 1);
    assertThat(sampler.isSampled(0L)).isTrue();
    when(System.nanoTime()).thenReturn(NANOS_PER_SECOND + 2);
    assertThat(sampler.isSampled(0L)).isTrue();
    when(System.nanoTime()).thenReturn(NANOS_PER_SECOND + 2);
    assertThat(sampler.isSampled(0L)).isFalse();
  }

  @Test public void resetsAfterASecond() {
    mockStatic(System.class);

    when(System.nanoTime()).thenReturn(NANOS_PER_SECOND);
    Sampler sampler = RateLimitingSampler.create(10);
    assertThat(sampler.isSampled(0L)).isTrue();
    assertThat(sampler.isSampled(0L)).isFalse();

    when(System.nanoTime()).thenReturn(NANOS_PER_SECOND + NANOS_PER_DECISECOND);
    assertThat(sampler.isSampled(0L)).isTrue();
    assertThat(sampler.isSampled(0L)).isFalse();

    when(System.nanoTime()).thenReturn(NANOS_PER_SECOND + NANOS_PER_DECISECOND * 9);
    assertThat(sampler.isSampled(0L)).isTrue();
    assertThat(sampler.isSampled(0L)).isTrue();
    assertThat(sampler.isSampled(0L)).isTrue();
    assertThat(sampler.isSampled(0L)).isTrue();
    assertThat(sampler.isSampled(0L)).isTrue();
    assertThat(sampler.isSampled(0L)).isTrue();
    assertThat(sampler.isSampled(0L)).isTrue();
    assertThat(sampler.isSampled(0L)).isTrue();
    assertThat(sampler.isSampled(0L)).isFalse();

    when(System.nanoTime()).thenReturn(NANOS_PER_SECOND + NANOS_PER_SECOND);
    assertThat(sampler.isSampled(0L)).isTrue();
  }

  @Test public void allowsOddRates() {
    mockStatic(System.class);

    when(System.nanoTime()).thenReturn(NANOS_PER_SECOND);
    Sampler sampler = RateLimitingSampler.create(11);
    when(System.nanoTime()).thenReturn(NANOS_PER_SECOND + NANOS_PER_DECISECOND * 9);
    for (int i = 0; i < 11; i++) {
      assertThat(sampler.isSampled(0L))
          .withFailMessage("failed after " + (i + 1))
          .isTrue();
    }
    assertThat(sampler.isSampled(0L)).isFalse();
  }

  @Test public void worksOnRollover() {
    mockStatic(System.class);
    when(System.nanoTime()).thenReturn(-NANOS_PER_SECOND);
    Sampler sampler = RateLimitingSampler.create(2);
    assertThat(sampler.isSampled(0L)).isTrue();

    when(System.nanoTime()).thenReturn(-NANOS_PER_SECOND / 2);
    assertThat(sampler.isSampled(0L)).isTrue(); // second request

    when(System.nanoTime()).thenReturn(-NANOS_PER_SECOND / 4);
    assertThat(sampler.isSampled(0L)).isFalse();

    when(System.nanoTime()).thenReturn(0L); // reset
    assertThat(sampler.isSampled(0L)).isTrue();
  }

  @DataPoints public static final int[] SAMPLE_RESERVOIRS = {1, 10, 100};

  @Theory public void retainsPerSampleRate(int reservoir) {
    Sampler sampler = RateLimitingSampler.create(reservoir);

    // parallel to ensure there aren't any unsynchronized race conditions
    long passed = new Random().longs(INPUT_SIZE).parallel().filter(sampler::isSampled).count();

    assertThat(passed).isEqualTo(reservoir);
  }

  @Test public void zeroMeansDropAllTraces() {
    assertThat(RateLimitingSampler.create(0)).isSameAs(Sampler.NEVER_SAMPLE);
  }

  @Test(expected = IllegalArgumentException.class)
  public void tracesPerSecond_cantBeNegative() {
    RateLimitingSampler.create(-1);
  }
}

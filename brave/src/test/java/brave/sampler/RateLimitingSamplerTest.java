/*
 * Copyright 2013-2023 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.sampler;

import brave.internal.Platform;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static brave.sampler.RateLimitingSampler.NANOS_PER_DECISECOND;
import static brave.sampler.RateLimitingSampler.NANOS_PER_SECOND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitingSamplerTest {
  @Mock Platform platform;

  @Test void samplesOnlySpecifiedNumber() {
    try (MockedStatic<Platform> mb = mockStatic(Platform.class)) {
      mb.when(Platform::get).thenReturn(platform);

      when(platform.nanoTime()).thenReturn(NANOS_PER_SECOND);
      Sampler sampler = RateLimitingSampler.create(2);

      when(platform.nanoTime()).thenReturn(NANOS_PER_SECOND + 1);
      assertThat(sampler.isSampled(0L)).isTrue();
      when(platform.nanoTime()).thenReturn(NANOS_PER_SECOND + 2);
      assertThat(sampler.isSampled(0L)).isTrue();
      when(platform.nanoTime()).thenReturn(NANOS_PER_SECOND + 2);
      assertThat(sampler.isSampled(0L)).isFalse();
    }
  }

  @Test void edgeCases() {
    try (MockedStatic<Platform> mb = mockStatic(Platform.class)) {
      mb.when(Platform::get).thenReturn(platform);

      when(platform.nanoTime()).thenReturn(NANOS_PER_SECOND);
      Sampler sampler = RateLimitingSampler.create(2);

      // exact moment of reset
      when(platform.nanoTime()).thenReturn(NANOS_PER_SECOND);
      assertThat(sampler.isSampled(0L)).isTrue();

      // right before next interval
      when(platform.nanoTime()).thenReturn(NANOS_PER_SECOND + NANOS_PER_SECOND - 1);
      assertThat(sampler.isSampled(0L)).isTrue();
      when(platform.nanoTime()).thenReturn(NANOS_PER_SECOND + NANOS_PER_SECOND - 1);
      assertThat(sampler.isSampled(0L)).isFalse();
    }
  }

  @Test void resetsAfterASecond() {
    try (MockedStatic<Platform> mb = mockStatic(Platform.class)) {
      mb.when(Platform::get).thenReturn(platform);

      when(platform.nanoTime()).thenReturn(NANOS_PER_SECOND);
      Sampler sampler = RateLimitingSampler.create(10);
      assertThat(sampler.isSampled(0L)).isTrue();
      assertThat(sampler.isSampled(0L)).isFalse();

      when(platform.nanoTime()).thenReturn(NANOS_PER_SECOND + NANOS_PER_DECISECOND);
      assertThat(sampler.isSampled(0L)).isTrue();
      assertThat(sampler.isSampled(0L)).isFalse();

      when(platform.nanoTime()).thenReturn(NANOS_PER_SECOND + NANOS_PER_DECISECOND * 9);
      assertThat(sampler.isSampled(0L)).isTrue();
      assertThat(sampler.isSampled(0L)).isTrue();
      assertThat(sampler.isSampled(0L)).isTrue();
      assertThat(sampler.isSampled(0L)).isTrue();
      assertThat(sampler.isSampled(0L)).isTrue();
      assertThat(sampler.isSampled(0L)).isTrue();
      assertThat(sampler.isSampled(0L)).isTrue();
      assertThat(sampler.isSampled(0L)).isTrue();
      assertThat(sampler.isSampled(0L)).isFalse();

      when(platform.nanoTime()).thenReturn(NANOS_PER_SECOND + NANOS_PER_SECOND);
      assertThat(sampler.isSampled(0L)).isTrue();
    }
  }

  @Test void resetsAfterALongGap() {
    try (MockedStatic<Platform> mb = mockStatic(Platform.class)) {
      mb.when(Platform::get).thenReturn(platform);

      when(platform.nanoTime()).thenReturn(0L);
      Sampler sampler = RateLimitingSampler.create(10);

      // Try a really long time later. Makes sure extra credit isn't given, and no recursion errors
      when(platform.nanoTime()).thenReturn(TimeUnit.DAYS.toNanos(365));
      assertThat(sampler.isSampled(0L)).isTrue();
      assertThat(sampler.isSampled(0L)).isFalse(); // we took the credit of the 1st decisecond
    }
  }

  @Test void worksWithEdgeCases() {
    try (MockedStatic<Platform> mb = mockStatic(Platform.class)) {
      mb.when(Platform::get).thenReturn(platform);

      when(platform.nanoTime()).thenReturn(0L);
      Sampler sampler = RateLimitingSampler.create(10);

      // try exact same nanosecond, however unlikely
      assertThat(sampler.isSampled(0L)).isTrue(); // 1

      // Try a value smaller than a decisecond, to ensure edge cases are covered
      when(platform.nanoTime()).thenReturn(1L);
      assertThat(sampler.isSampled(0L)).isFalse(); // credit used

      // Try exactly a decisecond later, which should be a reset condition
      when(platform.nanoTime()).thenReturn(NANOS_PER_DECISECOND);
      assertThat(sampler.isSampled(0L)).isTrue(); // 2
      assertThat(sampler.isSampled(0L)).isFalse(); // credit used

      // Try almost a second later
      when(platform.nanoTime()).thenReturn(NANOS_PER_SECOND - 1);
      assertThat(sampler.isSampled(0L)).isTrue(); // 3
      assertThat(sampler.isSampled(0L)).isTrue(); // 4
      assertThat(sampler.isSampled(0L)).isTrue(); // 5
      assertThat(sampler.isSampled(0L)).isTrue(); // 6
      assertThat(sampler.isSampled(0L)).isTrue(); // 7
      assertThat(sampler.isSampled(0L)).isTrue(); // 8
      assertThat(sampler.isSampled(0L)).isTrue(); // 9
      assertThat(sampler.isSampled(0L)).isTrue(); // 10
      assertThat(sampler.isSampled(0L)).isFalse(); // credit used

      // Try exactly a second later, which should be a reset condition
      when(platform.nanoTime()).thenReturn(NANOS_PER_SECOND);
      assertThat(sampler.isSampled(0L)).isTrue();
      assertThat(sampler.isSampled(0L)).isFalse(); // credit used
    }
  }

  @Test void allowsOddRates() {
    try (MockedStatic<Platform> mb = mockStatic(Platform.class)) {
      mb.when(Platform::get).thenReturn(platform);

      when(platform.nanoTime()).thenReturn(NANOS_PER_SECOND);
      Sampler sampler = RateLimitingSampler.create(11);
      when(platform.nanoTime()).thenReturn(NANOS_PER_SECOND + NANOS_PER_DECISECOND * 9);
      for (int i = 0; i < 11; i++) {
        assertThat(sampler.isSampled(0L))
          .withFailMessage("failed after " + (i + 1))
          .isTrue();
      }
      assertThat(sampler.isSampled(0L)).isFalse();
    }
  }

  @Test void worksOnRollover() {
    try (MockedStatic<Platform> mb = mockStatic(Platform.class)) {
      mb.when(Platform::get).thenReturn(platform);

      when(platform.nanoTime()).thenReturn(-NANOS_PER_SECOND);
      Sampler sampler = RateLimitingSampler.create(2);
      assertThat(sampler.isSampled(0L)).isTrue();

      when(platform.nanoTime()).thenReturn(-NANOS_PER_SECOND / 2);
      assertThat(sampler.isSampled(0L)).isTrue(); // second request

      when(platform.nanoTime()).thenReturn(-NANOS_PER_SECOND / 4);
      assertThat(sampler.isSampled(0L)).isFalse();

      when(platform.nanoTime()).thenReturn(0L); // reset
      assertThat(sampler.isSampled(0L)).isTrue();
    }
  }

  @Test void zeroMeansDropAllTraces() {
    assertThat(RateLimitingSampler.create(0)).isSameAs(Sampler.NEVER_SAMPLE);
  }

  @Test void tracesPerSecond_cantBeNegative() {
    assertThatThrownBy(() -> RateLimitingSampler.create(-1))
      .isInstanceOf(IllegalArgumentException.class);
  }
}

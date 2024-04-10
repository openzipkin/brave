/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.internal.recorder;

import brave.internal.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TickClockTest {
  @Mock Platform platform;

  @Test void relativeTimestamp_incrementsAccordingToNanoTick() {
    TickClock clock = new TickClock(platform, 1000L /* 1ms */, 0L /* 0ns */);

    when(platform.nanoTime()).thenReturn(1000L); // 1 microsecond = 1000 nanoseconds

    assertThat(clock.currentTimeMicroseconds()).isEqualTo(1001L); // 1ms + 1us
  }
}

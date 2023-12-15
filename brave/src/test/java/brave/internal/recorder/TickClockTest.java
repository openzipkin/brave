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

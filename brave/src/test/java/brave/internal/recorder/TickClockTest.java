package brave.internal.recorder;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
// Added to declutter console: tells power mock not to mess with implicit classes we aren't testing
@PowerMockIgnore({"org.apache.logging.*", "javax.script.*"})
@PrepareForTest(TickClock.class)
public class TickClockTest {

  @Test public void relativeTimestamp_incrementsAccordingToNanoTick() {
    mockStatic(System.class);
    TickClock clock = new TickClock(1000L /* 1ms */, 0L /* 0ns */);

    when(System.nanoTime()).thenReturn(1000L); // 1 microsecond = 1000 nanoseconds

    assertThat(clock.currentTimeMicroseconds()).isEqualTo(1001L); // 1ms + 1us
  }
}

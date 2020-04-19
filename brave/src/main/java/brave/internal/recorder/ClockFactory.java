package brave.internal.recorder;

import brave.Clock;

/**
 * Clock factory. Can be customized the clock instant
 *
 * @author wangliang <841369634@qq.com
 * @date 2020/4/19 14:45
 */
public class ClockFactory {

  private static Clock instant;

  public static Clock getInstant() {
    return instant;
  }

  public static void setInstant(Clock clock) {
    instant = clock;
  }

  public static Clock build(long baseEpochMicros, long baseTickNanos) {
    if (instant != null) {
      return instant;
    } else {
      return new TickClock(baseEpochMicros, baseTickNanos);
    }
  }
}

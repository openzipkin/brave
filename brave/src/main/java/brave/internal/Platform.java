package brave.internal;

import brave.Clock;
import brave.Tracer;
import brave.Tracing;
import com.google.auto.value.AutoValue;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.util.Enumeration;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jvnet.animal_sniffer.IgnoreJRERequirement;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

/**
 * Access to platform-specific features and implements a default logging spanReporter.
 *
 * <p>Originally designed by OkHttp team, derived from {@code okhttp3.internal.platform.Platform}
 */
public abstract class Platform {
  static final Logger logger = Logger.getLogger(Tracer.class.getName());

  private static final Platform PLATFORM = findPlatform();

  volatile Endpoint localEndpoint;

  /** Ensure we don't raise a {@linkplain ClassNotFoundException} calling deprecated methods */
  public abstract boolean zipkinV1Present();

  public Reporter<zipkin2.Span> reporter() {
    return LoggingReporter.INSTANCE;
  }

  enum LoggingReporter implements Reporter<zipkin2.Span> {
    INSTANCE;

    @Override public void report(Span span) {
      if (!logger.isLoggable(Level.INFO)) return;
      if (span == null) throw new NullPointerException("span == null");
      logger.info(span.toString());
    }

    @Override public String toString() {
      return "LoggingReporter{name=" + logger.getName() + "}";
    }
  }

  public Endpoint localEndpoint() {
    // uses synchronized variant of double-checked locking as getting the endpoint can be expensive
    if (localEndpoint == null) {
      synchronized (this) {
        if (localEndpoint == null) {
          localEndpoint = produceLocalEndpoint();
        }
      }
    }
    return localEndpoint;
  }

  Endpoint produceLocalEndpoint() {
    Endpoint.Builder builder = Endpoint.newBuilder().serviceName("unknown");
    try {
      Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
      if (nics == null) return builder.build();
      while (nics.hasMoreElements()) {
        NetworkInterface nic = nics.nextElement();
        Enumeration<InetAddress> addresses = nic.getInetAddresses();
        while (addresses.hasMoreElements()) {
          InetAddress address = addresses.nextElement();
          if (address.isSiteLocalAddress()) {
            builder.ip(address);
            break;
          }
        }
      }
    } catch (Exception e) {
      // don't crash the caller if there was a problem reading nics.
      if (logger.isLoggable(Level.FINE)) {
        logger.log(Level.FINE, "error reading nics", e);
      }
    }
    return builder.build();
  }

  public static Platform get() {
    return PLATFORM;
  }

  /** Attempt to match the host runtime to a capable Platform implementation. */
  static Platform findPlatform() {
    // Find Zipkin v1 methods
    boolean zipkinV1Present;
    try {
      Class.forName("zipkin.Endpoint");
      zipkinV1Present = true;
    } catch (ClassNotFoundException e) {
      zipkinV1Present = false;
    }

    Platform jre9 = Jre9.buildIfSupported(zipkinV1Present);

    if (jre9 != null) return jre9;

    Platform jre7 = Jre7.buildIfSupported(zipkinV1Present);

    if (jre7 != null) return jre7;

    // compatible with JRE 6
    return Jre6.build(zipkinV1Present);
  }

  /**
   * This class uses pseudo-random number generators to provision IDs.
   *
   * <p>This optimizes speed over full coverage of 64-bits, which is why it doesn't share a {@link
   * SecureRandom}. It will use {@link java.util.concurrent.ThreadLocalRandom} unless used in JRE 6
   * which doesn't have the class.
   */
  public abstract long randomLong();

  /**
   * Returns the high 8-bytes for use in {@link Tracing.Builder#traceId128Bit 128-bit trace IDs}.
   *
   * <p>The upper 4-bytes are epoch seconds and the lower 4-bytes are random. This makes it
   * convertible to <a href="http://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-request-tracing.html"></a>Amazon
   * X-Ray trace ID format v1</a>.
   */
  public abstract long nextTraceIdHigh();

  public Clock clock() {
    return new TickClock(System.currentTimeMillis() * 1000);
  }

  /** gets a timestamp based on duration since the create tick. */
  static final class TickClock implements Clock {
    final long baseEpochMicros;
    final long tickNanos;

    TickClock(long baseEpochMicros) {
      this.baseEpochMicros = baseEpochMicros;
      tickNanos = System.nanoTime();
    }

    @Override public long currentTimeMicroseconds() {
      return ((System.nanoTime() - tickNanos) / 1000) + baseEpochMicros;
    }

    @Override public String toString() {
      return "TickClock{"
          + "baseEpochMicros=" + baseEpochMicros + ", "
          + "tickNanos=" + tickNanos
          + "}";
    }
  }

  @AutoValue
  static abstract class Jre9 extends Platform {

    static Jre9 buildIfSupported(boolean zipkinV1Present) {
      // Find JRE 9 new methods
      try {
        Class zoneId = Class.forName("java.time.ZoneId");
        Class.forName("java.time.Clock").getMethod("tickMillis", zoneId);
        return new AutoValue_Platform_Jre9(zipkinV1Present);
      } catch (ClassNotFoundException e) {
        // pre JRE 8
      } catch (NoSuchMethodException e) {
        // pre JRE 9
      }
      return null;
    }

    @IgnoreJRERequirement
    @Override public Clock clock() {
      java.time.Instant instant = java.time.Clock.systemUTC().instant();
      long epochMicros = (instant.getEpochSecond() * 1000000) + (instant.getNano() / 1000);
      return new TickClock(epochMicros);
    }

    @IgnoreJRERequirement
    @Override public long randomLong() {
      return java.util.concurrent.ThreadLocalRandom.current().nextLong();
    }

    @IgnoreJRERequirement
    @Override public long nextTraceIdHigh() {
      return nextTraceIdHigh(java.util.concurrent.ThreadLocalRandom.current());
    }
  }

  @AutoValue
  static abstract class Jre7 extends Platform {

    static Jre7 buildIfSupported(boolean zipkinV1Present) {
      // Find JRE 7 new methods
      try {
        Class.forName("java.util.concurrent.ThreadLocalRandom");
        return new AutoValue_Platform_Jre7(zipkinV1Present);
      } catch (ClassNotFoundException e) {
        // pre JRE 7
      }
      return null;
    }

    @IgnoreJRERequirement
    @Override public long randomLong() {
      return java.util.concurrent.ThreadLocalRandom.current().nextLong();
    }

    @IgnoreJRERequirement
    @Override public long nextTraceIdHigh() {
      return nextTraceIdHigh(java.util.concurrent.ThreadLocalRandom.current());
    }
  }

  static long nextTraceIdHigh(Random prng) {
    long epochSeconds = System.currentTimeMillis() / 1000;
    int random = prng.nextInt();
    return (epochSeconds & 0xffffffffL) << 32
        | (random & 0xffffffffL);
  }

  @AutoValue
  static abstract class Jre6 extends Platform {
    abstract Random prng();

    static Jre6 build(boolean zipkinV1Present) {
      return new AutoValue_Platform_Jre6(zipkinV1Present, new Random(System.nanoTime()));
    }

    @Override public long randomLong() {
      return prng().nextLong();
    }

    @Override public long nextTraceIdHigh() {
      return nextTraceIdHigh(prng());
    }
  }
}

package com.twitter.zipkin.gen;

import com.github.kristofa.brave.internal.Nullable;
import java.io.Serializable;
import javax.annotation.Generated;

import static zipkin.internal.Util.equal;

/**
 * An annotation is similar to a log statement. It includes a host field which
 * allows these events to be attributed properly, and also aggregatable.
 */
@Generated("thrift")
public class Annotation implements Serializable {
  static final long serialVersionUID = 1L;

  public static Annotation create(long timestamp, String value, @Nullable Endpoint endpoint) {
    return new Annotation(timestamp, value, endpoint);
  }

  /**
   * Microseconds from epoch.
   * 
   * This value should use the most precise value possible. For example,
   * gettimeofday or syncing nanoTime against a tick of currentTimeMillis.
   */
  public final long timestamp; // required
  public final String value; // required
  /**
   * Always the host that recorded the event. By specifying the host you allow
   * rollup of all events (such as client requests to a service) by IP address.
   */
  public final Endpoint host; // optional

  Annotation(long timestamp, String value, Endpoint host) {
    this.timestamp = timestamp;
    this.value = value;
    this.host = host;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Annotation) {
      Annotation that = (Annotation) o;
      return (this.timestamp == that.timestamp)
          && (this.value.equals(that.value))
          && equal(this.host, that.host);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= (timestamp >>> 32) ^ timestamp;
    h *= 1000003;
    h ^= value.hashCode();
    h *= 1000003;
    h ^= (host == null) ? 0 : host.hashCode();
    return h;
  }
}


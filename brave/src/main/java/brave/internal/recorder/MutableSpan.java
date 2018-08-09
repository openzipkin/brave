package brave.internal.recorder;

import brave.Span.Kind;
import brave.Tracer;
import brave.internal.IpLiteral;
import brave.internal.Nullable;
import brave.propagation.TraceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import zipkin2.Span;

/**
 * This represents a span except for its {@link TraceContext}. It is mutable, for late adjustments.
 *
 * <p>While in-flight, the data is synchronized where necessary. When exposed to users, it can be
 * mutated without synchronization.
 */
public final class MutableSpan {
  /*
   * One of these objects is allocated for each in-flight span, so we try to be parsimonious on things
   * like array allocation and object reference size.
   */
  Kind kind;
  boolean shared;
  long startTimestamp, finishTimestamp;
  String name, remoteServiceName, remoteIp;
  int remotePort;

  /**
   * To reduce the amount of allocation, collocate annotations with tags in a pair-indexed list.
   * This will be (startTimestamp, value) for annotations and (key, value) for tags.
   */
  final List<Object> pairs = new ArrayList<>(6); // assume 3 tags and no annotations

  /** @see brave.Span#start(long) */
  public void startTimestamp(long startTimestamp) {
    this.startTimestamp = startTimestamp;
  }

  /** @see brave.Span#name(String) */
  public void name(String name) {
    if (name == null) throw new NullPointerException("name == null");
    this.name = name;
  }

  /** @see brave.Span#kind(brave.Span.Kind) */
  public void kind(Kind kind) {
    if (kind == null) throw new NullPointerException("kind == null");
    this.kind = kind;
  }

  /** @see brave.Span#remoteServiceName(String) */
  @Nullable public String remoteServiceName() {
    return remoteServiceName;
  }

  /** @see brave.Span#remoteServiceName(String) */
  public void remoteServiceName(String remoteServiceName) {
    if (remoteServiceName == null || remoteServiceName.isEmpty()) {
      throw new NullPointerException("remoteServiceName is empty");
    }
    this.remoteServiceName = remoteServiceName.toLowerCase(Locale.ROOT);
  }

  /**
   * The text representation of the primary IPv4 or IPv6 address associated with the remote side of
   * this connection. Ex. 192.168.99.100 null if unknown.
   *
   * @see brave.Span#remoteIpAndPort(String, int)
   */
  @Nullable public String remoteIp() {
    return remoteIp;
  }

  /**
   * Port of the remote IP's socket or 0, if not known.
   *
   * @see java.net.InetSocketAddress#getPort()
   * @see brave.Span#remoteIpAndPort(String, int)
   */
  public int remotePort() {
    return remotePort;
  }

  /** @see brave.Span#remoteIpAndPort(String, int) */
  public boolean remoteIpAndPort(@Nullable String remoteIp, int remotePort) {
    if (remoteIp == null) return false;
    this.remoteIp = IpLiteral.ipOrNull(remoteIp);
    if (this.remoteIp == null) return false;
    if (remotePort > 0xffff) throw new IllegalArgumentException("invalid port " + remotePort);
    if (remotePort < 0) remotePort = 0;
    this.remotePort = remotePort;
    return true;
  }

  /** @see brave.Span#annotate(String) */
  public void annotate(long timestamp, String value) {
    if (value == null) throw new NullPointerException("value == null");
    if (timestamp == 0L) return;
    pairs.add(timestamp);
    pairs.add(value);
  }

  /** @see brave.Span#tag(String, String) */
  public void tag(String key, String value) {
    if (key == null) throw new NullPointerException("key == null");
    if (key.isEmpty()) throw new IllegalArgumentException("key is empty");
    if (value == null) throw new NullPointerException("value == null");
    for (int i = 0, length = pairs.size(); i < length; i += 2) {
      if (key.equals(pairs.get(i))) {
        pairs.set(i + 1, value);
        return;
      }
    }
    pairs.add(key);
    pairs.add(value);
  }

  /**
   * Indicates we are contributing to a span started by another tracer (ex on a different host).
   * Defaults to false.
   *
   * @see Tracer#joinSpan(TraceContext)
   * @see zipkin2.Span#shared()
   */
  public void setShared() {
    shared = true;
  }

  /** @see brave.Span#finish(long) */
  public void finishTimestamp(long finishTimestamp) {
    this.finishTimestamp = finishTimestamp;
  }

  // Since this is not exposed, this class could be refactored later as needed to act in a pool
  // to reduce GC churn. This would involve calling span.clear and resetting the fields below.
  public void writeTo(zipkin2.Span.Builder result) {
    result.name(name);
    if (kind != null && kind.ordinal() < Span.Kind.values().length) { // defend against version skew
      result.kind(zipkin2.Span.Kind.values()[kind.ordinal()]);
    }
    result.timestamp(startTimestamp);
    if (startTimestamp != 0 && finishTimestamp != 0L) {
      result.duration(Math.max(finishTimestamp - startTimestamp, 1));
    }
    if (remoteIp != null || remoteServiceName != null) {
      result.remoteEndpoint(zipkin2.Endpoint.newBuilder()
          .serviceName(remoteServiceName)
          .ip(remoteIp)
          .port(remotePort)
          .build());
    }

    for (int i = 0, length = pairs.size(); i < length; i += 2) {
      Object first = pairs.get(i);
      String second = pairs.get(i + 1).toString();
      if (first instanceof Long) {
        result.addAnnotation((Long) first, second);
      } else {
        result.putTag(first.toString(), second);
      }
    }
    if (shared) result.shared(true);
  }

  MutableSpan() { // intentionally hidden
  }
}

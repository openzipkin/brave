package brave.internal.recorder;

import brave.Span.Kind;
import brave.Tracer;
import brave.internal.IpLiteral;
import brave.internal.Nullable;
import brave.propagation.TraceContext;
import java.util.ArrayList;
import java.util.Locale;

/**
 * This represents a span except for its {@link TraceContext}. It is mutable, for late adjustments.
 *
 * <p>While in-flight, the data is synchronized where necessary. When exposed to users, it can be
 * mutated without synchronization.
 */
public final class MutableSpan implements Cloneable {

  public interface TagConsumer<T> {
    void accept(T target, String key, String value);
  }

  public interface AnnotationConsumer<T> {
    void accept(T target, long timestamp, String value);
  }

  /*
   * One of these objects is allocated for each in-flight span, so we try to be parsimonious on things
   * like array allocation and object reference size.
   */
  Kind kind;
  boolean shared;
  long startTimestamp, finishTimestamp;
  String name, remoteServiceName, remoteIp;
  int remotePort;

  /** To reduce the amount of allocation use a pair-indexed list for tag (key, value). */
  ArrayList<String> tags;
  /** Also use pair indexing for annotations, but type object to store (startTimestamp, value). */
  ArrayList<Object> annotations;

  public MutableSpan() {
    // this cheats because it will not need to grow unless there are more than 5 tags
    tags = new ArrayList<>();
    // lazy initialize annotations
  }

  /** Returns the {@link brave.Span#name(String) span name} or null */
  @Nullable public String name() {
    return name;
  }

  /** @see brave.Span#name(String) */
  public void name(String name) {
    if (name == null) throw new NullPointerException("name == null");
    this.name = name;
  }

  /** Returns the {@link brave.Span#start(long) span start timestamp} or zero */
  public long startTimestamp() {
    return startTimestamp;
  }

  /** @see brave.Span#start(long) */
  public void startTimestamp(long startTimestamp) {
    this.startTimestamp = startTimestamp;
  }

  /** Returns the {@link brave.Span#finish(long) span finish timestamp} or zero */
  public long finishTimestamp() {
    return finishTimestamp;
  }

  /** @see brave.Span#finish(long) */
  public void finishTimestamp(long finishTimestamp) {
    this.finishTimestamp = finishTimestamp;
  }

  /** Returns the {@link brave.Span#kind(brave.Span.Kind) span kind} or null */
  public Kind kind() {
    return kind;
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
    if (annotations == null) annotations = new ArrayList<>();
    annotations.add(timestamp);
    annotations.add(value);
  }

  /** @see brave.Span#tag(String, String) */
  public void tag(String key, String value) {
    if (key == null) throw new NullPointerException("key == null");
    if (key.isEmpty()) throw new IllegalArgumentException("key is empty");
    if (value == null) throw new NullPointerException("value == null");
    for (int i = 0, length = tags.size(); i < length; i += 2) {
      if (key.equals(tags.get(i))) {
        tags.set(i + 1, value);
        return;
      }
    }
    tags.add(key);
    tags.add(value);
  }

  public <T> void forEachTag(TagConsumer<T> tagConsumer, T target) {
    for (int i = 0, length = tags.size(); i < length; i += 2) {
      tagConsumer.accept(target, tags.get(i), tags.get(i + 1));
    }
  }

  public <T> void forEachAnnotation(AnnotationConsumer<T> annotationConsumer, T target) {
    if (annotations == null) return;
    for (int i = 0, length = annotations.size(); i < length; i += 2) {
      long timestamp = (long) annotations.get(i);
      annotationConsumer.accept(target, timestamp, annotations.get(i + 1).toString());
    }
  }

  /** Returns true if the span ID is {@link #setShared() shared} with a remote client. */
  public boolean shared() {
    return shared;
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
}

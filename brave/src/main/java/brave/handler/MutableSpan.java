/*
 * Copyright 2013-2020 The OpenZipkin Authors
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
package brave.handler;

import brave.Span.Kind;
import brave.Tag;
import brave.Tracer;
import brave.internal.IpLiteral;
import brave.internal.Nullable;
import brave.propagation.TraceContext;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * This represents a span except for its {@link TraceContext}. It is mutable, for late adjustments.
 *
 * <p>While in-flight, the data is synchronized where necessary. When exposed to users, it can be
 * mutated without synchronization.
 */
public final class MutableSpan implements Cloneable {

  public interface TagConsumer<T> {
    /** @see brave.Span#tag(String, String) */
    void accept(T target, String key, String value);
  }

  public interface AnnotationConsumer<T> {
    /** @see brave.Span#annotate(long, String) */
    void accept(T target, long timestamp, String value);
  }

  public interface TagUpdater {
    /**
     * Returns the same value, an updated one, or null to drop the tag.
     *
     * @see brave.Span#tag(String, String)
     */
    @Nullable String update(String key, String value);
  }

  public interface AnnotationUpdater {
    /**
     * Returns the same value, an updated one, or null to drop the annotation.
     *
     * @see brave.Span#annotate(long, String)
     */
    @Nullable String update(long timestamp, String value);
  }

  /*
   * One of these objects is allocated for each in-flight span, so we try to be parsimonious on things
   * like array allocation and object reference size.
   */
  Kind kind;
  boolean shared;
  long startTimestamp, finishTimestamp;
  String name, localServiceName, localIp, remoteServiceName, remoteIp;
  int localPort, remotePort;

  /** To reduce the amount of allocation use a pair-indexed list for tag (key, value). */
  ArrayList<String> tags;
  /** Also use pair indexing for annotations, but type object to store (startTimestamp, value). */
  ArrayList<Object> annotations;
  Throwable error;

  public MutableSpan() {
    // this cheats because it will not need to grow unless there are more than 5 tags
    tags = new ArrayList<>();
    // lazy initialize annotations
  }

  /** Returns true if there was no data added. Usually this indicates an instrumentation bug. */
  public boolean isEmpty() {
    return kind == null
      && !shared
      && startTimestamp == 0L
      && finishTimestamp == 0L
      && name == null
      && localServiceName == null
      && localIp == null
      && remoteServiceName == null
      && remoteIp == null
      && localPort == 0
      && remotePort == 0
      && tags.isEmpty()
      && annotations == null
      && error == null;
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
  public void kind(@Nullable Kind kind) {
    this.kind = kind;
  }

  /** When null {@link brave.Tracing.Builder#localServiceName(String) default} is used. */
  @Nullable public String localServiceName() {
    return localServiceName;
  }

  /** @see brave.Tracing.Builder#localServiceName(String) */
  public void localServiceName(String localServiceName) {
    if (localServiceName == null || localServiceName.isEmpty()) {
      throw new NullPointerException("localServiceName is empty");
    }
    this.localServiceName = localServiceName;
  }

  /** When null {@link brave.Tracing.Builder#localIp(String) default} will be used for zipkin. */
  @Nullable public String localIp() {
    return localIp;
  }

  /** @see #localIp() */
  public boolean localIp(@Nullable String localIp) {
    this.localIp = IpLiteral.ipOrNull(localIp);
    return localIp != null;
  }

  /** When zero {@link brave.Tracing.Builder#localIp(String) default} will be used for zipkin. */
  public int localPort() {
    return localPort;
  }

  /** @see #localPort() */
  public void localPort(int localPort) {
    if (localPort > 0xffff) throw new IllegalArgumentException("invalid port " + localPort);
    if (localPort < 0) localPort = 0;
    this.localPort = localPort;
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
    this.remoteServiceName = remoteServiceName;
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

  /** Returns true if an annotation with the given value exists in this span. */
  public boolean containsAnnotation(String value) {
    if (value == null) throw new NullPointerException("value == null");
    if (annotations == null) return false;

    for (int i = 0, length = annotations.size(); i < length; i += 2) {
      if (value.equals(annotations.get(i + 1))) return true;
    }
    return false;
  }

  /** @see brave.Span#annotate(String) */
  public void annotate(long timestamp, String value) {
    if (value == null) throw new NullPointerException("value == null");
    if (timestamp == 0L) return;
    if (annotations == null) annotations = new ArrayList<>();
    annotations.add(timestamp);
    annotations.add(value);
  }

  /** @see brave.Span#error(Throwable) */
  public Throwable error() {
    return error;
  }

  /** @see brave.Span#error(Throwable) */
  public void error(Throwable error) {
    this.error = error;
  }

  /** Returns the last value associated with the key or null */
  @Nullable public String tag(String key) {
    if (key == null) throw new NullPointerException("key == null");
    if (key.isEmpty()) throw new IllegalArgumentException("key is empty");
    String result = null;
    for (int i = 0, length = tags.size(); i < length; i += 2) {
      if (key.equals(tags.get(i))) result = tags.get(i + 1);
    }
    return result;
  }

  /**
   * @see brave.Span#tag(String, String)
   * @see Tag#tag(Object, TraceContext, MutableSpan)
   */
  public void tag(String key, String value) {
    if (key == null) throw new NullPointerException("key == null");
    if (key.isEmpty()) throw new IllegalArgumentException("key is empty");
    if (value == null) throw new NullPointerException("value of " + key + " == null");
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

  /** Allows you to update values for redaction purposes */
  public void forEachTag(TagUpdater tagUpdater) {
    for (int i = 0, length = tags.size(); i < length; i += 2) {
      String value = tags.get(i + 1);
      String newValue = tagUpdater.update(tags.get(i), value);
      if (updateOrRemove(tags, i, value, newValue)) {
        length -= 2;
        i -= 2;
      }
    }
  }

  /**
   * Allows you to copy all data into a different target, such as a different span model or logs.
   */
  public <T> void forEachAnnotation(AnnotationConsumer<T> annotationConsumer, T target) {
    if (annotations == null) return;
    for (int i = 0, length = annotations.size(); i < length; i += 2) {
      long timestamp = (long) annotations.get(i);
      annotationConsumer.accept(target, timestamp, annotations.get(i + 1).toString());
    }
  }

  /** Allows you to update values for redaction purposes */
  public void forEachAnnotation(AnnotationUpdater annotationUpdater) {
    if (annotations == null) return;
    for (int i = 0, length = annotations.size(); i < length; i += 2) {
      String value = annotations.get(i + 1).toString();
      String newValue = annotationUpdater.update((long) annotations.get(i), value);
      if (updateOrRemove(annotations, i, value, newValue)) {
        length -= 2;
        i -= 2;
      }
    }
  }

  /** Returns true if the key/value was removed from the pair-indexed list at index {@code i} */
  static boolean updateOrRemove(ArrayList list, int i, Object value, @Nullable Object newValue) {
    if (newValue == null) {
      list.remove(i);
      list.remove(i);
      return true;
    } else if (!value.equals(newValue)) {
      list.set(i + 1, newValue);
    }
    return false;
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

  @Override public int hashCode() {
    return super.hashCode(); // quiet error-prone
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    // Hack that allows WeakConcurrentMap to lookup without allocating a new object.
    if (o instanceof WeakReference) o = ((WeakReference) o).get();
    if (!(o instanceof MutableSpan)) return false;
    return super.equals(o); // not doing value-based comparison
  }
}

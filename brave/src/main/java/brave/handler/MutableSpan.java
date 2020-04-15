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

import brave.ErrorParser;
import brave.Span.Kind;
import brave.SpanCustomizer;
import brave.internal.IpLiteral;
import brave.internal.Nullable;
import brave.propagation.TraceContext;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

import static brave.internal.InternalPropagation.FLAG_DEBUG;
import static brave.internal.InternalPropagation.FLAG_SHARED;
import static brave.internal.JsonEscaper.jsonEscape;

/**
 * This represents a span except for its {@link TraceContext}. It is mutable, for late adjustments.
 *
 * <p>While in-flight, the data is synchronized where necessary. When exposed to users, it can be
 * mutated without synchronization.
 *
 * @since 5.4
 */
public final class MutableSpan implements Cloneable {
  static final MutableSpan EMPTY = new MutableSpan();

  /** @since 5.4 */
  public interface TagConsumer<T> {
    /** @see brave.Span#tag(String, String) */
    void accept(T target, String key, String value);
  }

  /** @since 5.4 */
  public interface AnnotationConsumer<T> {
    /** @see brave.Span#annotate(long, String) */
    void accept(T target, long timestamp, String value);
  }

  /** @since 5.4 */
  public interface TagUpdater {
    /**
     * Returns the same value, an updated one, or null to drop the tag.
     *
     * @see brave.Span#tag(String, String)
     */
    @Nullable String update(String key, String value);
  }

  /** @since 5.4 */
  public interface AnnotationUpdater {
    /**
     * Returns the same value, an updated one, or null to drop the annotation.
     *
     * @see brave.Span#annotate(long, String)
     */
    @Nullable String update(long timestamp, String value);
  }

  /*
   * One of these objects is allocated for each in-flight span, so we try to be parsimonious on
   * things like array allocation and object reference size.
   */
  String traceId, localRootId, parentId, id;
  Kind kind;
  int flags;
  long startTimestamp, finishTimestamp;
  String name, localServiceName, localIp, remoteServiceName, remoteIp;
  int localPort, remotePort;

  /** To reduce the amount of allocation use a pair-indexed list for tag (key, value). */
  ArrayList<String> tags;
  /** Also use pair indexing for annotations, but type object to store (startTimestamp, value). */
  ArrayList<Object> annotations;
  Throwable error;

  /** @since 5.4 */
  public MutableSpan() {
  }

  /**
   * Creates a new instance from the given context, and defaults in the span.
   *
   * <p><em>Note:</em> It is unexpected to have context properties also in the span defaults. The
   * context will win in this case, as opposed to throwing an exception.
   *
   * @since 5.12
   */
  public MutableSpan(TraceContext context, @Nullable MutableSpan defaults) {
    this(defaults != null ? defaults : EMPTY);
    if (context == null) throw new NullPointerException("context == null");
    traceId(context.traceIdString());
    localRootId(context.localRootIdString());
    parentId(context.parentIdString());
    id(context.spanIdString());
    flags = 0; // don't inherit flags from the span
    if (context.debug()) setDebug();
    if (context.shared()) setShared();
  }

  /** @since 5.12 */
  public MutableSpan(MutableSpan toCopy) {
    if (toCopy == null) throw new NullPointerException("toCopy == null");
    if (toCopy == EMPTY) return;
    traceId = toCopy.traceId;
    localRootId = toCopy.localRootId;
    parentId = toCopy.parentId;
    id = toCopy.id;
    kind = toCopy.kind;
    flags = toCopy.flags;
    startTimestamp = toCopy.startTimestamp;
    finishTimestamp = toCopy.finishTimestamp;
    name = toCopy.name;
    localServiceName = toCopy.localServiceName;
    localIp = toCopy.localIp;
    localPort = toCopy.localPort;
    remoteServiceName = toCopy.remoteServiceName;
    remoteIp = toCopy.remoteIp;
    remotePort = toCopy.remotePort;
    tags = toCopy.tags != null ? new ArrayList<>(toCopy.tags) : null;
    annotations = toCopy.annotations != null ? new ArrayList<>(toCopy.annotations) : null;
    error = toCopy.error;
  }

  /**
   * @since 5.4
   * @deprecated Since 5.12 use {@link #equals(Object)} against a base value.
   */
  @Deprecated public boolean isEmpty() {
    return equals(EMPTY);
  }

  /**
   * Returns the {@linkplain TraceContext#traceIdString() trace ID}
   *
   * @since 5.12
   */
  public String traceId() {
    return traceId;
  }

  /**
   * Calling this overrides the {@linkplain TraceContext#traceIdString() trace ID}.
   *
   * @see #traceId()
   */
  public void traceId(String traceId) {
    if (traceId == null) throw new NullPointerException("traceId == null");
    if (traceId.isEmpty()) throw new NullPointerException("traceId is empty");
    this.traceId = traceId;
  }

  /**
   * Returns the {@linkplain TraceContext#localRootIdString() local root ID}
   *
   * @since 5.12
   */
  @Nullable public String localRootId() {
    return localRootId;
  }

  /**
   * Calling this overrides the {@linkplain TraceContext#localRootIdString() local root ID}.
   *
   * @see #localRootId()
   */
  public void localRootId(@Nullable String localRootId) {
    this.localRootId = localRootId == null || localRootId.isEmpty() ? null : localRootId;
  }

  /**
   * Returns the {@linkplain TraceContext#parentIdString() parent ID} or {@code null}
   *
   * @since 5.12
   */
  @Nullable public String parentId() {
    return parentId;
  }

  /**
   * Calling this overrides the {@linkplain TraceContext#parentIdString() parent ID}.
   *
   * @see #parentId()
   */
  public void parentId(@Nullable String parentId) {
    this.parentId = parentId == null || parentId.isEmpty() ? null : parentId;
  }

  /**
   * Returns the {@linkplain TraceContext#spanId() span ID}.
   *
   * @since 5.12
   */
  public String id() {
    return id;
  }

  /**
   * Calling this overrides the {@linkplain TraceContext#spanId() span ID}.
   *
   * @see #id()
   */
  public void id(String id) {
    if (id == null) throw new NullPointerException("id == null");
    if (id.isEmpty()) throw new NullPointerException("id is empty");
    this.id = id;
  }

  /**
   * Returns the {@linkplain brave.SpanCustomizer#name(String) span name} or {@code null}
   *
   * @since 5.4
   */
  @Nullable public String name() {
    return name;
  }

  /**
   * Calling this overrides any previous value, such as{@link brave.SpanCustomizer#name(String)}.
   *
   * @see #name()
   */
  public void name(@Nullable String name) {
    this.name = name == null || name.isEmpty() ? null : name;
  }

  /**
   * Returns the {@linkplain brave.Span#start(long) span start timestamp} or zero.
   *
   * @since 5.4
   */
  public long startTimestamp() {
    return startTimestamp;
  }

  /**
   * Calling this overrides any previous value, such as {@link brave.Span#start(long)} or {@link
   * brave.Tracer#startScopedSpan(String)}.
   *
   * @see #startTimestamp()
   */
  public void startTimestamp(long startTimestamp) {
    this.startTimestamp = startTimestamp;
  }

  /**
   * Returns the {@linkplain brave.Span#finish(long) span finish timestamp} or zero.
   *
   * @since 5.4
   */
  public long finishTimestamp() {
    return finishTimestamp;
  }

  /**
   * Calling this overrides any previous value, such as {@link brave.Span#finish(long)} or {@link
   * brave.ScopedSpan#finish()}.
   *
   * @see #finishTimestamp()
   */
  public void finishTimestamp(long finishTimestamp) {
    this.finishTimestamp = finishTimestamp;
  }

  /**
   * Returns the {@linkplain brave.Span#kind(brave.Span.Kind) span kind} or {@code null}.
   *
   * @since 5.4
   */
  public Kind kind() {
    return kind;
  }

  /**
   * Calling this overrides any previous value, such as {@link brave.Span#kind(Kind).
   *
   * @see #kind()
   */
  public void kind(@Nullable Kind kind) {
    this.kind = kind;
  }

  /**
   * Returns the {@linkplain brave.Tracing.Builder#localIp(String) label of this node in the service
   * graph} or {@code null}.
   *
   * <p><em>Note</em>: This is initialized from {@link brave.Tracing.Builder#localServiceName(String)}.
   * {@linkplain FinishedSpanHandler handlers} that want to conditionally replace the value should
   * compare against the same value given to the tracing component.
   *
   * @since 5.4
   */
  @Nullable public String localServiceName() {
    return localServiceName;
  }

  /**
   * Calling this overrides any previous value, such as {@link brave.Tracing.Builder#localServiceName(String)}.
   *
   * @see #localServiceName()
   */
  public void localServiceName(@Nullable String localServiceName) {
    if (localServiceName == null || localServiceName.isEmpty()) {
      this.localServiceName = null;
    }
    this.localServiceName = localServiceName;
  }

  /**
   * Returns the {@linkplain brave.Tracing.Builder#localIp(String) primary IP address associated
   * with this service} or {@code null}.
   *
   * <p><em>Note</em>: This is initialized from {@link brave.Tracing.Builder#localIp(String)}.
   * {@linkplain FinishedSpanHandler handlers} that want to conditionally replace the value should
   * compare against the same value given to the tracing component.
   *
   * @since 5.4
   */
  @Nullable public String localIp() {
    return localIp;
  }

  /**
   * Calling this overrides any previous value, such as {@link brave.Tracing.Builder#localIp(String)}.
   *
   * @see #localIp()
   */
  public boolean localIp(@Nullable String localIp) {
    this.localIp = IpLiteral.ipOrNull(localIp);
    return localIp != null;
  }

  /**
   * Returns the {@linkplain brave.Tracing.Builder#localPort(int) primary listen port associated
   * with this service} or zero.
   *
   * <p><em>Note</em>: This is initialized from {@link brave.Tracing.Builder#localPort(int)}.
   * {@linkplain FinishedSpanHandler handlers} that want to conditionally replace the value should
   * compare against the same value given to the tracing component.
   *
   * @since 5.4
   */
  public int localPort() {
    return localPort;
  }

  /**
   * Calling this overrides any previous value, such as {@link brave.Tracing.Builder#localPort(int)}.
   *
   * @see #localPort()
   */
  public void localPort(int localPort) {
    if (localPort > 0xffff) throw new IllegalArgumentException("invalid port " + localPort);
    if (localPort < 0) localPort = 0;
    this.localPort = localPort;
  }

  /**
   * Returns the {@linkplain brave.Span#remoteServiceName(String) primary label of the remote
   * service} or {@code null}.
   *
   * @see #remoteIp()
   * @see #remotePort()
   * @since 5.4
   */
  @Nullable public String remoteServiceName() {
    return remoteServiceName;
  }

  /**
   * Calling this overrides any previous value, such as {@link brave.Span#remoteServiceName(String)}.
   *
   * @see #remoteServiceName()
   */
  public void remoteServiceName(@Nullable String remoteServiceName) {
    if (remoteServiceName == null || remoteServiceName.isEmpty()) {
      this.remoteServiceName = null;
    }
    this.remoteServiceName = remoteServiceName;
  }

  /**
   * Returns the {@linkplain brave.Span#remoteIpAndPort(String, int) IP of the remote service} or
   * {@code null}.
   *
   * @see #remoteServiceName()
   * @see #remotePort()
   * @since 5.4
   */
  @Nullable public String remoteIp() {
    return remoteIp;
  }

  /**
   * Returns the {@linkplain brave.Span#remoteIpAndPort(String, int) port of the remote service} or
   * zero.
   *
   * @see #remoteServiceName()
   * @see #remoteIp()
   * @since 5.4
   */
  public int remotePort() {
    return remotePort;
  }

  /**
   * Calling this overrides any previous value, such as {@link brave.Span#remoteIpAndPort(String,
   * int)}.
   *
   * @see #remoteServiceName()
   * @see #remoteIp()
   * @see #remotePort()
   */
  public boolean remoteIpAndPort(@Nullable String remoteIp, int remotePort) {
    if (remoteIp == null) return false;
    this.remoteIp = IpLiteral.ipOrNull(remoteIp);
    if (this.remoteIp == null) return false;
    if (remotePort > 0xffff) throw new IllegalArgumentException("invalid port " + remotePort);
    if (remotePort < 0) remotePort = 0;
    this.remotePort = remotePort;
    return true;
  }

  /**
   * Returns the {@linkplain brave.Span#error(Throwable) error} or {@code null}.
   *
   * @since 5.4
   */
  public Throwable error() {
    return error;
  }

  /**
   * Calling this overrides any previous value, such as {@link brave.Span#error(Throwable).
   *
   * @see #error()
   */
  public void error(@Nullable Throwable error) {
    this.error = error;
  }

  /**
   * Returns true if the context was {@linkplain TraceContext#debug() debug}.
   *
   * @since 5.4
   */
  public boolean debug() {
    return (flags & FLAG_DEBUG) == FLAG_DEBUG;
  }

  /**
   * Calling this is unexpected as it should only be initialized by {@link TraceContext#debug()}.
   *
   * @see #debug()
   */
  public void setDebug() {
    flags |= FLAG_DEBUG;
  }

  /**
   * Returns true if the context was {@linkplain TraceContext#shared() shared}.
   *
   * @since 5.4
   */
  public boolean shared() {
    return (flags & FLAG_SHARED) == FLAG_SHARED;
  }

  /**
   * Calling this is unexpected as it should only be initialized by {@link TraceContext#shared()}.
   *
   * @see #shared()
   */
  public void setShared() {
    flags |= FLAG_SHARED;
  }

  /** @since 5.12 */
  public int annotationCount() {
    return annotations != null ? annotations.size() / 2 : 0;
  }

  /**
   * Iterates over all {@linkplain SpanCustomizer#annotate(String) annotations} for purposes such as
   * copying values.
   *
   * <p>Ex.
   * <pre>{@code
   * // During initialization, cache an annotation consumer function:
   * annotationConsumer = (target, timestamp, value) -> target.add(tuple(timestamp, value));
   *
   * // Re-use that function while processing spans.
   * List<Tuple<Long, String>> list = new ArrayList<>();
   * span.forEachAnnotation(annotationConsumer, list);
   * }</pre>
   *
   * @see #forEachAnnotation(AnnotationUpdater)
   * @since 5.4
   */
  public <T> void forEachAnnotation(AnnotationConsumer<T> annotationConsumer, T target) {
    if (annotations == null) return;
    for (int i = 0, length = annotations.size(); i < length; i += 2) {
      long timestamp = (long) annotations.get(i);
      annotationConsumer.accept(target, timestamp, annotations.get(i + 1).toString());
    }
  }

  /**
   * Allows you to update or drop {@linkplain SpanCustomizer#annotate(String) annotations} for
   * purposes such as redaction.
   *
   * <p>Ex.
   * <pre>{@code
   * // During initialization, cache an annotation updater function:
   * annotationRedacter = (timestamp, value) -> badWords.contains(value) ? null : value;
   *
   * // Re-use that function while processing spans.
   * span.forEachAnnotation(annotationRedacter);
   * }</pre>
   *
   * @see #forEachAnnotation(AnnotationConsumer, Object)
   * @since 5.4
   */
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

  /**
   * Returns true if an annotation with the given value exists in this span.
   *
   * @see #forEachAnnotation(AnnotationConsumer, Object)
   * @see #forEachAnnotation(AnnotationUpdater)
   * @since 5.4
   */
  public boolean containsAnnotation(String value) {
    if (value == null) throw new NullPointerException("value == null");
    if (annotations == null) return false;

    for (int i = 0, length = annotations.size(); i < length; i += 2) {
      if (value.equals(annotations.get(i + 1))) return true;
    }
    return false;
  }

  /**
   * Calling this adds an annotation, such as done in {@link brave.SpanCustomizer#annotate(String)}.
   *
   * @see #forEachAnnotation(AnnotationConsumer, Object)
   * @see #forEachAnnotation(AnnotationUpdater)
   * @since 5.4
   */
  public void annotate(long timestamp, String value) {
    if (value == null) throw new NullPointerException("value == null");
    if (timestamp == 0L) return;
    if (annotations == null) annotations = new ArrayList<>();
    annotations.add(timestamp);
    annotations.add(value);
  }

  /** @since 5.12 */
  public int tagCount() {
    return tags != null ? tags.size() / 2 : 0;
  }

  /**
   * Returns the last {@linkplain brave.SpanCustomizer#tag(String, String) tag value} associated
   * with the key or {@code null}.
   *
   * @since 5.4
   */
  @Nullable public String tag(String key) {
    if (key == null) throw new NullPointerException("key == null");
    if (key.isEmpty()) throw new IllegalArgumentException("key is empty");
    if (tags == null) return null;
    String result = null;
    for (int i = 0, length = tags.size(); i < length; i += 2) {
      if (key.equals(tags.get(i))) result = tags.get(i + 1);
    }
    return result;
  }

  /**
   * Iterates over all {@linkplain SpanCustomizer#tag(String, String) tags} for purposes such as
   * copying values.
   *
   * <p>Ex.
   * <pre>{@code
   * Map<String, String> tags = new LinkedHashMap<>();
   * span.forEachTag(Map::put, tags);
   * }</pre>
   *
   * @see #forEachTag(TagUpdater)
   * @see #tag(String)
   * @since 5.4
   */
  public <T> void forEachTag(TagConsumer<T> tagConsumer, T target) {
    if (tags == null) return;
    for (int i = 0, length = tags.size(); i < length; i += 2) {
      tagConsumer.accept(target, tags.get(i), tags.get(i + 1));
    }
  }

  /**
   * Allows you to update or drop {@linkplain SpanCustomizer#tag(String, String) tags} for purposes
   * such as redaction.
   *
   * <p>Ex.
   * <pre>{@code
   * // During initialization, cache an tag updater function:
   * tagRedacter = (key, value) -> badWords.contains(value) ? null : value;
   *
   * // Re-use that function while processing spans.
   * span.forEachTag(tagRedacter);
   * }</pre>
   *
   * @see #forEachTag(TagConsumer, Object)
   * @see #tag(String)
   * @since 5.4
   */
  public void forEachTag(TagUpdater tagUpdater) {
    if (tags == null) return;
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
   * Calling this overrides any previous value, such as {@link brave.SpanCustomizer#tag(String,
   * String)}.
   *
   * @see #tag(String)
   */
  public void tag(String key, String value) {
    if (key == null) throw new NullPointerException("key == null");
    if (key.isEmpty()) throw new IllegalArgumentException("key is empty");
    if (value == null) throw new NullPointerException("value of " + key + " == null");
    if (tags == null) {
      // this will not need to grow unless there are more than 5 tags
      tags = new ArrayList<>();
    }
    for (int i = 0, length = tags.size(); i < length; i += 2) {
      if (key.equals(tags.get(i))) {
        tags.set(i + 1, value);
        return;
      }
    }
    tags.add(key);
    tags.add(value);
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

  volatile int hashCode; // Lazily initialized and cached.

  @Override public int hashCode() {
    int h = hashCode;
    if (h == 0) {
      h = 1000003;
      h ^= traceId == null ? 0 : traceId.hashCode();
      h *= 1000003;
      h ^= localRootId == null ? 0 : localRootId.hashCode();
      h *= 1000003;
      h ^= parentId == null ? 0 : parentId.hashCode();
      h *= 1000003;
      h ^= id == null ? 0 : id.hashCode();
      h *= 1000003;
      h ^= kind == null ? 0 : kind.hashCode();
      h *= 1000003;
      h ^= flags;
      h *= 1000003;
      h ^= (int) ((startTimestamp >>> 32) ^ startTimestamp);
      h *= 1000003;
      h ^= (int) ((finishTimestamp >>> 32) ^ finishTimestamp);
      h *= 1000003;
      h ^= name == null ? 0 : name.hashCode();
      h *= 1000003;
      h ^= localServiceName == null ? 0 : localServiceName.hashCode();
      h *= 1000003;
      h ^= localIp == null ? 0 : localIp.hashCode();
      h *= 1000003;
      h ^= localPort;
      h *= 1000003;
      h ^= remoteServiceName == null ? 0 : remoteServiceName.hashCode();
      h *= 1000003;
      h ^= remoteIp == null ? 0 : remoteIp.hashCode();
      h *= 1000003;
      h ^= remotePort;
      h *= 1000003;
      h ^= tags == null ? 0 : tags.hashCode();
      h *= 1000003;
      h ^= annotations == null ? 0 : annotations.hashCode();
      h *= 1000003;
      h ^= error == null ? 0 : error.hashCode();
      hashCode = h;
    }
    return h;
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    // Hack that allows WeakConcurrentMap to lookup without allocating a new object.
    if (o instanceof WeakReference) o = ((WeakReference) o).get();
    if (!(o instanceof MutableSpan)) return false;

    MutableSpan that = (MutableSpan) o;
    return equal(traceId, that.traceId)
      && equal(localRootId, that.localRootId)
      && equal(parentId, that.parentId)
      && equal(id, that.id)
      && kind == that.kind
      && flags == that.flags
      && startTimestamp == that.startTimestamp
      && finishTimestamp == that.finishTimestamp
      && equal(name, that.name)
      && equal(localServiceName, that.localServiceName)
      && equal(localIp, that.localIp)
      && localPort == that.localPort
      && equal(remoteServiceName, that.remoteServiceName)
      && equal(remoteIp, that.remoteIp)
      && remotePort == that.remotePort
      && equal(tags, that.tags)
      && equal(annotations, that.annotations)
      && equal(error, that.error);
  }

  /** Writes this span in Zipkin V2 format */
  // Ported from zipkin2.internal.V2SpanWriter and may eventually move to a separate codec type
  @Override public String toString() {
    StringBuilder b = new StringBuilder();
    if (traceId != null) {
      b.append("\"traceId\":\"");
      b.append(traceId);
      b.append('"');
    }
    if (parentId != null) {
      b.append(",\"parentId\":\"");
      b.append(parentId);
      b.append('"');
    }
    if (id != null) {
      b.append(",\"id\":\"");
      b.append(id);
      b.append('"');
    }
    if (kind != null) {
      b.append(",\"kind\":\"");
      b.append(kind.toString());
      b.append('"');
    }
    if (name != null) {
      b.append(",\"name\":\"");
      jsonEscape(name, b);
      b.append('"');
    }
    if (startTimestamp != 0L) {
      b.append(",\"timestamp\":");
      b.append(startTimestamp);
      if (finishTimestamp != 0L) {
        b.append(",\"duration\":");
        b.append(finishTimestamp - startTimestamp);
      }
    }
    if (localServiceName != null || localIp != null) {
      b.append(",\"localEndpoint\":");
      writeEndpoint(b, localServiceName, localIp, localPort);
    }
    if (remoteServiceName != null || remoteIp != null) {
      b.append(",\"remoteEndpoint\":");
      writeEndpoint(b, remoteServiceName, remoteIp, remotePort);
    }
    int annotationLength = annotations != null ? annotations.size() : 0;
    if (annotationLength > 0) {
      b.append(",\"annotations\":");
      b.append('[');
      for (int i = 0; i < annotationLength; ) {
        b.append("{\"timestamp\":");
        b.append(annotations.get(i++));
        b.append(",\"value\":\"");
        jsonEscape(annotations.get(i++).toString(), b);
        b.append('}');
        if (i < annotationLength) b.append(',');
      }
      b.append(']');
    }
    int tagLength = tags != null ? tags.size() : 0;
    if (tagLength > 0 || error != null) {
      b.append(",\"tags\":{");
      boolean wroteError = false;
      for (int i = 0; i < tagLength; ) {
        String key = tags.get(i++);
        if (key.equals("error")) wroteError = true;
        writeKeyValue(b, key, tags.get(i++));
        if (i < tagLength) b.append(',');
      }
      if (error != null && !wroteError) {
        if (tagLength > 0) b.append(',');
        writeKeyValue(b, "error", ErrorParser.parse(error));
      }
      b.append('}');
    }
    if (debug()) b.append(",\"debug\":true");
    if (shared()) b.append(",\"shared\":true");
    b.append('}');
    if (b.charAt(0) == ',') {
      b.setCharAt(0, '{');
    } else {
      b.insert(0, '{');
    }
    return b.toString();
  }

  void writeKeyValue(StringBuilder b, String key, String value) {
    b.append('"');
    jsonEscape(key, b);
    b.append("\":\"");
    jsonEscape(value, b);
    b.append('"');
  }

  static void writeEndpoint(StringBuilder b,
    @Nullable String serviceName, @Nullable String ip, int port) {
    b.append('{');
    boolean wroteField = false;
    if (serviceName != null) {
      b.append("\"serviceName\":\"");
      jsonEscape(serviceName, b);
      b.append('"');
      wroteField = true;
    }
    if (ip != null) {
      if (wroteField) b.append(',');
      if (IpLiteral.detectFamily(ip) == IpLiteral.IpFamily.IPv4) {
        b.append("\"ipv4\":\"");
      } else {
        b.append("\"ipv6\":\"");
      }
      b.append(ip);
      b.append('"');
      wroteField = true;
    }
    if (port != 0) {
      if (wroteField) b.append(',');
      b.append("\"port\":");
      b.append(port);
    }
    b.append('}');
  }

  static boolean equal(@Nullable Object a, @Nullable Object b) {
    return a == null ? b == null : a.equals(b); // Java 6 can't use Objects.equals()
  }
}

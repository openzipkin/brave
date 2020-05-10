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
import brave.SpanCustomizer;
import brave.Tags;
import brave.internal.Nullable;
import brave.internal.codec.IpLiteral;
import brave.internal.collect.UnsafeArrayMap;
import brave.propagation.TraceContext;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import static brave.internal.InternalPropagation.FLAG_DEBUG;
import static brave.internal.InternalPropagation.FLAG_SHARED;
import static brave.internal.codec.JsonEscaper.jsonEscape;

/**
 * This represents a span except for its {@link TraceContext}. It is mutable, for late adjustments.
 *
 * <h3>Notes</h3>
 * <p>Between {@link SpanHandler#begin(TraceContext, MutableSpan, TraceContext)} and
 * {@link SpanHandler#end(TraceContext, MutableSpan, SpanHandler.Cause)}, Brave owns this reference,
 * synchronizing where necessary as updates come from different application threads.
 *
 * <p>Upon end, Brave no longer makes updates. It invokes each {@link SpanHandler}, one-by-one on
 * the same thread. This means subsequent handlers do not have to synchronize to view updates from a
 * prior one. However, it does imply they must make mutations on the same thread.
 *
 * <p>In other words, this type is not thread safe. If you need to mutate this span in a different
 * thread, use the {@linkplain #MutableSpan(MutableSpan) copy constructor}.
 *
 * @since 5.4
 */
public final class MutableSpan implements Cloneable {
  static final Object[] EMPTY_ARRAY = new Object[0];
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
  Throwable error;

  //
  // The below use object arrays instead of ArrayList. The intent is not for safe sharing
  // (copy-on-write), as this type is externally synchronized. In other words, this isn't
  // copy-on-write. We just grow arrays as we need to similar to how ArrayList does it.
  //
  // tags [(key, value)] annotations [(timestamp, value)]
  Object[] tags = EMPTY_ARRAY, annotations = EMPTY_ARRAY;
  int tagCount, annotationCount;

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
    if (toCopy.equals(EMPTY)) return;
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
    // In case this is a default span, don't hold a reference to the same array!
    tags = copy(toCopy.tags);
    tagCount = toCopy.tagCount;
    annotations = copy(toCopy.annotations);
    annotationCount = toCopy.annotationCount;
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
   * Returns the {@linkplain brave.Tracing.Builder#localServiceName(String) label of this node in
   * the service graph} or {@code null}.
   *
   * <p><em>Note</em>: This is initialized from {@link brave.Tracing.Builder#localServiceName(String)}.
   * {@linkplain SpanHandler handlers} that want to conditionally replace the value should compare
   * against the same value given to the tracing component.
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
   * {@linkplain SpanHandler handlers} that want to conditionally replace the value should compare
   * against the same value given to the tracing component.
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
   * {@linkplain SpanHandler handlers} that want to conditionally replace the value should compare
   * against the same value given to the tracing component.
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
   * Calling this overrides any previous value, such as from {@link brave.Span#remoteIpAndPort(String,
   * int)}.
   *
   * @see #remoteIpAndPort(String, int)
   * @see 5.12
   */
  public void remoteIp(@Nullable String remoteIp) {
    this.remoteIp = IpLiteral.ipOrNull(remoteIp);
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
   * Calling this overrides any previous value, such as from {@link brave.Span#remoteIpAndPort(String,
   * int)}.
   *
   * @see #remoteIpAndPort(String, int)
   * @see 5.12
   */
  public void remotePort(int remotePort) {
    if (remotePort > 0xffff) throw new IllegalArgumentException("invalid port " + remotePort);
    if (remotePort < 0) remotePort = 0;
    this.remotePort = remotePort;
  }

  /**
   * When {@code remoteIp} is not {@code null}, calling this overrides any previous value, such as
   * {@link brave.Span#remoteIpAndPort(String, int)}.
   *
   * @see #remoteServiceName()
   * @see #remoteIp()
   * @see #remotePort()
   */
  public boolean remoteIpAndPort(@Nullable String remoteIp, int remotePort) {
    if (remoteIp == null) return false;
    this.remoteIp = IpLiteral.ipOrNull(remoteIp);
    if (this.remoteIp == null) return false;
    remotePort(remotePort);
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
    return annotationCount;
  }

  /**
   * A read-only view of the current annotations as a collection of {@code (epochMicroseconds ->
   * value)}.
   *
   * @see #forEachAnnotation(AnnotationConsumer, Object)
   * @since 5.12
   */
  public Collection<Map.Entry<Long, String>> annotations() {
    return UnsafeArrayMap.<Long, String>newBuilder().build(annotations).entrySet();
  }

  /**
   * Iterates over all {@linkplain SpanCustomizer#annotate(String) annotations} for purposes such as
   * copying values. Unlike {@link #annotations()}, using this is allocation free.
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
   * @see #annotations()
   * @since 5.4
   */
  public <T> void forEachAnnotation(AnnotationConsumer<T> annotationConsumer, T target) {
    for (int i = 0, length = annotationCount * 2; i < length; i += 2) {
      long timestamp = (long) annotations[i];
      annotationConsumer.accept(target, timestamp, annotations[i + 1].toString());
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
    for (int i = 0, length = annotationCount * 2; i < length; i += 2) {
      String value = annotations[i + 1].toString();
      String newValue = annotationUpdater.update((long) annotations[i], value);
      if (newValue != null) {
        update(annotations, i, newValue);
      } else {
        remove(annotations, i);
        length -= 2;
        annotationCount--;
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
    for (int i = 0, length = annotationCount * 2; i < length; i += 2) {
      if (value.equals(annotations[i + 1])) return true;
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
    if (timestamp == 0L) return; // silently ignore data Zipkin would drop
    annotations =
        add(annotations, annotationCount * 2, timestamp, value); // Annotations are always add.
    annotationCount++;
  }

  /** @since 5.12 */
  public int tagCount() {
    return tagCount;
  }

  /**
   * A read-only view of the current tags a map.
   *
   * @see #forEachTag(TagConsumer, Object)
   * @since 5.12
   */
  public Map<String, String> tags() {
    return UnsafeArrayMap.<String, String>newBuilder().build(tags);
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
    for (int i = 0, length = tagCount * 2; i < length; i += 2) {
      if (key.equals(tags[i])) return (String) tags[i + 1];
    }
    return null;
  }

  /**
   * Iterates over all {@linkplain SpanCustomizer#tag(String, String) tags} for purposes such as
   * copying values. Unlike {@link #tags()}, using this is allocation free.
   *
   * <p>Ex.
   * <pre>{@code
   * Map<String, String> tags = new LinkedHashMap<>();
   * span.forEachTag(Map::put, tags);
   * }</pre>
   *
   * @see #forEachTag(TagUpdater)
   * @see #tag(String)
   * @see #tags()
   * @since 5.4
   */
  public <T> void forEachTag(TagConsumer<T> tagConsumer, T target) {
    for (int i = 0, length = tagCount * 2; i < length; i += 2) {
      tagConsumer.accept(target, (String) tags[i], (String) tags[i + 1]);
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
    for (int i = 0, length = tagCount * 2; i < length; i += 2) {
      String value = (String) tags[i + 1];
      String newValue = tagUpdater.update((String) tags[i], value);
      if (newValue != null) {
        update(tags, i, newValue);
      } else {
        remove(tags, i);
        length -= 2;
        tagCount--;
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
    int i = 0;
    for (int length = tagCount * 2; i < length; i += 2) {
      if (key.equals(tags[i])) {
        update(tags, i, value);
        return;
      }
    }
    tags = add(tags, i, key, value);
    tagCount++;
  }

  @Override public int hashCode() {
    int h = 1000003; // mutable! cannot cache hashCode
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
    h ^= entriesHashCode(tags, tagCount);
    h *= 1000003;
    h ^= entriesHashCode(annotations, annotationCount);
    h *= 1000003;
    h ^= error == null ? 0 : error.hashCode();
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
        && entriesEqual(tags, tagCount, that.tags, that.tagCount)
        && entriesEqual(annotations, annotationCount, that.annotations, that.annotationCount)
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
    if (annotationCount > 0) {
      b.append(",\"annotations\":");
      b.append('[');
      for (int i = 0, length = annotationCount * 2; i < length; ) {
        b.append("{\"timestamp\":");
        b.append(annotations[i]);
        b.append(",\"value\":\"");
        jsonEscape(annotations[i + 1].toString(), b);
        b.append('}');
        i += 2;
        if (i < length) b.append(',');
      }
      b.append(']');
    }
    if (tagCount > 0 || error != null) {
      b.append(",\"tags\":{");
      boolean wroteError = false;
      for (int i = 0, length = tagCount * 2; i < length; ) {
        String key = (String) tags[i];
        if (key.equals("error")) wroteError = true;
        writeKeyValue(b, key, (String) tags[i + 1]);
        i += 2;
        if (i < length) b.append(',');
      }
      if (error != null && !wroteError) {
        if (tagCount > 0) b.append(',');
        MutableSpan errorCatcher = new MutableSpan();
        Tags.ERROR.tag(error, null, errorCatcher);
        writeKeyValue(b, "error", errorCatcher.tag("error"));
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

  static Object[] add(Object[] input, int i, Object key, Object value) {
    Object[] result;
    if (i == input.length) {
      result = Arrays.copyOf(input, i + 2); // grow for one more entry
    } else {
      result = input;
    }
    result[i] = key;
    result[i + 1] = value;
    return result;
  }

  // this is externally synchronized, so we can edit it directly
  static void update(Object[] input, int i, Object value) {
    if (value.equals(input[i + 1])) return;
    input[i + 1] = value;
  }

  // This shifts and backfills nulls so that we don't thrash copying arrays
  // when deleting. UnsafeArray will still work as it skips on first null key.
  static void remove(Object[] input, int i) {
    int j = i + 2;
    for (; j < input.length; i += 2, j += 2) {
      if (input[j] == null) break; // found null key
      input[i] = input[j];
      input[i + 1] = input[j + 1];
    }
    input[i] = input[i + 1] = null;
  }

  static Object[] copy(Object[] input) {
    return input.length > 0 ? Arrays.copyOf(input, input.length) : EMPTY_ARRAY;
  }

  static boolean entriesEqual(Object[] left, int leftCount, Object[] right, int rightCount) {
    if (leftCount != rightCount) return false;
    for (int i = 0; i < leftCount * 2; i++) {
      if (!equal(left[i], right[i])) return false;
    }
    return true;
  }

  static int entriesHashCode(Object[] entries, int count) {
    int h = 1000003; // mutable! cannot cache hashCode
    for (int i = 0; i < count * 2; i++) {
      h ^= entries[i] == null ? 0 : entries[i].hashCode();
      h *= 1000003;
    }
    return h;
  }

  static boolean equal(@Nullable Object a, @Nullable Object b) {
    return a == null ? b == null : a.equals(b); // Java 6 can't use Objects.equals()
  }
}

package com.twitter.zipkin.gen;

import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.internal.Util;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.github.kristofa.brave.internal.Util.equal;

/** This is an internal type representing a span in the trace tree. */
public class Span implements Serializable {

  /** Internal. Do not create spans directly */
  public static Span create(SpanId spanId) {
    return new Span(spanId);
  }

  static final long serialVersionUID = 1L;

  private final SpanId context; // nullable for deprecated constructor
  private long trace_id; // required
  private long trace_id_high; // optional (default to zero)
  private String name; // required
  private long id; // required
  private Long parent_id; // optional
  private List<Annotation> annotations = Collections.emptyList(); // required
  private List<BinaryAnnotation> binary_annotations = Collections.emptyList(); // required
  private Boolean debug; // optional
  private Long timestamp; // optional
  private Long duration; // optional

  /** @deprecated internally we call {@link Span#Span(SpanId)} because it sets the identity */
  @Deprecated
  public Span() {
    context = null;
  }

  Span(SpanId context) {
    this.context = context;
    trace_id_high = context.traceIdHigh;
    trace_id = context.traceId;
    parent_id = context.nullableParentId();
    id = context.spanId;
    name = ""; // avoid NPE on equals
    if (context.debug()) debug = true;
  }

  /**
   * Internal api that returns the trace context this span was created with.
   *
   * @since 3.17
   */
  public SpanId context() {
    return context;
  }

  public long getTrace_id() {
    return this.trace_id;
  }

  /** @deprecated do not modify the context of a span once created */
  @Deprecated
  public Span setTrace_id(long trace_id) {
    this.trace_id = trace_id;
    return this;
  }

  /**
   * When non-zero, the trace containing this span uses 128-bit trace identifiers.
   *
   * @since 3.15
   * @see zipkin.Span#traceIdHigh
   */
  public long getTrace_id_high() {
    return this.trace_id_high;
  }

  /** @deprecated do not modify the context of a span once created */
  @Deprecated
  public Span setTrace_id_high(long trace_id_high) {
    this.trace_id_high = trace_id_high;
    return this;
  }

  /**
   * Span name in lowercase, rpc method for example
   * 
   * Conventionally, when the span name isn't known, name = "unknown".
   */
  public String getName() {
    return this.name;
  }

  /**
   * Span name in lowercase, rpc method for example
   * 
   * Conventionally, when the span name isn't known, name = "unknown".
   */
  public Span setName(String name) {
    if (name != null) {
      name = name.toLowerCase();
    }
    this.name = name;
    return this;
  }

  public long getId() {
    return this.id;
  }

  /** @deprecated do not modify the context of a span once created */
  @Deprecated
  public Span setId(long id) {
    this.id = id;
    return this;
  }

  public Long getParent_id() {
    return this.parent_id;
  }

  /** @deprecated do not modify the context of a span once created */
  @Deprecated
  public Span setParent_id(Long parent_id) {
    this.parent_id = parent_id;
    return this;
  }

  public Span addToAnnotations(Annotation elem) {
    if (this.annotations == Collections.EMPTY_LIST) {
      this.annotations = new ArrayList<Annotation>();
    }
    this.annotations.add(elem);
    return this;
  }

  public List<Annotation> getAnnotations() {
    return Collections.unmodifiableList(this.annotations);
  }

  public Span setAnnotations(List<Annotation> annotations) {
    if (this.annotations != Collections.EMPTY_LIST) this.annotations.clear();
    for (Annotation a : annotations) addToAnnotations(a);
    return this;
  }

  public Span addToBinary_annotations(BinaryAnnotation elem) {
    if (this.binary_annotations == Collections.EMPTY_LIST) {
      this.binary_annotations = new ArrayList<BinaryAnnotation>();
    }
    this.binary_annotations.add(elem);
    return this;
  }

  public List<BinaryAnnotation> getBinary_annotations() {
    return Collections.unmodifiableList(this.binary_annotations);
  }

  public Span setBinaryAnnotations(List<BinaryAnnotation> binary_annotations) {
    if (this.binary_annotations != Collections.EMPTY_LIST) this.binary_annotations.clear();
    for (BinaryAnnotation b : binary_annotations) addToBinary_annotations(b);
    return this;
  }

  public Boolean isDebug() {
    return this.debug;
  }

  /** @deprecated do not modify the context of a span once created */
  @Deprecated
  public Span setDebug(Boolean debug) {
    this.debug = debug;
    return this;
  }

  /**
   * Microseconds from epoch of the creation of this span.
   * 
   * This value should be set directly by instrumentation, using the most
   * precise value possible. For example, gettimeofday or syncing nanoTime
   * against a tick of currentTimeMillis.
   * 
   * For compatibilty with instrumentation that precede this field, collectors
   * or span stores can derive this via Annotation.timestamp.
   * For example, SERVER_RECV.timestamp or CLIENT_SEND.timestamp.
   * 
   * This field is optional for compatibility with old data: first-party span
   * stores are expected to support this at time of introduction.
   */
  public Long getTimestamp() {
    return this.timestamp;
  }

  /**
   * Microseconds from epoch of the creation of this span.
   * 
   * This value should be set directly by instrumentation, using the most
   * precise value possible. For example, gettimeofday or syncing nanoTime
   * against a tick of currentTimeMillis.
   * 
   * For compatibilty with instrumentation that precede this field, collectors
   * or span stores can derive this via Annotation.timestamp.
   * For example, SERVER_RECV.timestamp or CLIENT_SEND.timestamp.
   * 
   * This field is optional for compatibility with old data: first-party span
   * stores are expected to support this at time of introduction.
   */
  public Span setTimestamp(Long timestamp) {
    this.timestamp = timestamp;
    return this;
  }

  /**
   * Measurement of duration in microseconds, used to support queries.
   * 
   * This value should be set directly, where possible. Doing so encourages
   * precise measurement decoupled from problems of clocks, such as skew or NTP
   * updates causing time to move backwards.
   * 
   * For compatibilty with instrumentation that precede this field, collectors
   * or span stores can derive this by subtracting Annotation.timestamp.
   * For example, SERVER_SEND.timestamp - SERVER_RECV.timestamp.
   * 
   * If this field is persisted as unset, zipkin will continue to work, except
   * duration query support will be implementation-specific. Similarly, setting
   * this field non-atomically is implementation-specific.
   * 
   * This field is i64 vs i32 to support spans longer than 35 minutes.
   */
  public Long getDuration() {
    return this.duration;
  }

  /**
   * Measurement of duration in microseconds, used to support queries.
   * 
   * This value should be set directly, where possible. Doing so encourages
   * precise measurement decoupled from problems of clocks, such as skew or NTP
   * updates causing time to move backwards.
   * 
   * For compatibilty with instrumentation that precede this field, collectors
   * or span stores can derive this by subtracting Annotation.timestamp.
   * For example, SERVER_SEND.timestamp - SERVER_RECV.timestamp.
   * 
   * If this field is persisted as unset, zipkin will continue to work, except
   * duration query support will be implementation-specific. Similarly, setting
   * this field non-atomically is implementation-specific.
   * 
   * This field is i64 vs i32 to support spans longer than 35 minutes.
   */
  public Span setDuration(Long duration) {
    this.duration = duration;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof Span)) return false;
    Span that = (Span) o;
    return (this.trace_id_high == that.trace_id_high)
        && (this.trace_id == that.trace_id)
        && (this.name.equals(that.name))
        && (this.id == that.id)
        && equal(this.parent_id, that.parent_id)
        && equal(this.timestamp, that.timestamp)
        && equal(this.duration, that.duration)
        && equal(this.annotations, that.annotations)
        && equal(this.binary_annotations, that.binary_annotations)
        && equal(this.debug, that.debug);
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= (trace_id_high >>> 32) ^ trace_id_high;
    h *= 1000003;
    h ^= (trace_id >>> 32) ^ trace_id;
    h *= 1000003;
    h ^= name.hashCode();
    h *= 1000003;
    h ^= (id >>> 32) ^ id;
    h *= 1000003;
    h ^= (parent_id == null) ? 0 : parent_id.hashCode();
    h *= 1000003;
    h ^= (timestamp == null) ? 0 : timestamp.hashCode();
    h *= 1000003;
    h ^= (duration == null) ? 0 : duration.hashCode();
    h *= 1000003;
    h ^= (annotations == null) ? 0 : annotations.hashCode();
    h *= 1000003;
    h ^= (binary_annotations == null) ? 0 : binary_annotations.hashCode();
    h *= 1000003;
    h ^= (debug == null) ? 0 : debug.hashCode();
    return h;
  }

  @Override
  public String toString() {
    return new String(SpanCodec.JSON.writeSpan(this), Util.UTF_8);
  }
}


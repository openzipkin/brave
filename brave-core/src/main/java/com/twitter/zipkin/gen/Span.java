package com.twitter.zipkin.gen;

import com.github.kristofa.brave.internal.Util;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.github.kristofa.brave.internal.Util.equal;

/**
 * A trace is a series of spans (often RPC calls) which form a latency tree.
 * 
 * The root span is where trace_id = id and parent_id = Nil. The root span is
 * usually the longest interval in the trace, starting with a SERVER_RECV
 * annotation and ending with a SERVER_SEND.
 */
public class Span implements Serializable {

  static final long serialVersionUID = 1L;

  /**
   * Internal field, used for deriving duration with {@link System#nanoTime()}.
   */
  public volatile Long startTick;

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

  public long getTrace_id() {
    return this.trace_id;
  }

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

  public Span setId(long id) {
    this.id = id;
    return this;
  }

  public Long getParent_id() {
    return this.parent_id;
  }

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
    return this.annotations;
  }

  public Span setAnnotations(List<Annotation> annotations) {
    this.annotations = annotations;
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
    return this.binary_annotations;
  }

  public Span setBinaryAnnotations(List<BinaryAnnotation> binary_annotations) {
    this.binary_annotations = binary_annotations;
    return this;
  }

  public Boolean isDebug() {
    return this.debug;
  }

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
    if (o == this) {
      return true;
    }
    if (o instanceof Span) {
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
    return false;
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

  /** Changes this to a zipkin-native span object. */
  public zipkin.Span toZipkin() {
    zipkin.Span.Builder result = zipkin.Span.builder();
    result.traceId(getTrace_id());
    result.traceIdHigh(getTrace_id_high());
    result.id(getId());
    result.parentId(getParent_id());
    result.name(getName());
    result.timestamp(getTimestamp());
    result.duration(getDuration());
    result.debug(isDebug());
    for (Annotation a : getAnnotations()) {
      result.addAnnotation(zipkin.Annotation.create(a.timestamp, a.value, from(a.host)));
    }
    for (BinaryAnnotation a : getBinary_annotations()) {
      result.addBinaryAnnotation(zipkin.BinaryAnnotation.builder()
          .key(a.key)
          .value(a.value)
          .type(zipkin.BinaryAnnotation.Type.fromValue(a.type.getValue()))
          .endpoint(from(a.host))
          .build());
    }
    return result.build();
  }

  private static zipkin.Endpoint from(Endpoint host) {
    if (host == null) return null;
    return zipkin.Endpoint.builder()
        .ipv4(host.ipv4)
        .ipv6(host.ipv6)
        .port(host.port)
        .serviceName(host.service_name).build();
  }
}


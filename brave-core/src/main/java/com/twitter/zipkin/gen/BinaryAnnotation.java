package com.twitter.zipkin.gen;

import com.github.kristofa.brave.internal.Nullable;
import java.io.Serializable;
import java.util.Arrays;

import static com.github.kristofa.brave.internal.Util.UTF_8;
import static com.github.kristofa.brave.internal.Util.checkNotBlank;
import static com.github.kristofa.brave.internal.Util.checkNotNull;
import static com.github.kristofa.brave.internal.Util.equal;

/**
 * Binary annotations are tags applied to a Span to give it context. For
 * example, a binary annotation of "http.uri" could the path to a resource in a
 * RPC call.
 * 
 * Binary annotations of type STRING are always queryable, though more a
 * historical implementation detail than a structural concern.
 * 
 * Binary annotations can repeat, and vary on the host. Similar to Annotation,
 * the host indicates who logged the event. This allows you to tell the
 * difference between the client and server side of the same key. For example,
 * the key "http.uri" might be different on the client and server side due to
 * rewriting, like "/api/v1/myresource" vs "/myresource. Via the host field,
 * you can see the different points of view, which often help in debugging.
 */
public class BinaryAnnotation implements Serializable {

  static final long serialVersionUID = 1L;

  /**
   * Special-cased form supporting {@link zipkinCoreConstants#CLIENT_ADDR} and {@link
   * zipkinCoreConstants#SERVER_ADDR}.
   *
   * @param key {@link zipkinCoreConstants#CLIENT_ADDR} or {@link zipkinCoreConstants#SERVER_ADDR}
   * @param endpoint associated endpoint.
   */
  public static BinaryAnnotation address(String key, Endpoint endpoint) {
    return create(key, new byte[] {1}, AnnotationType.BOOL, checkNotNull(endpoint, "endpoint"));
  }

  /** String values are the only queryable type of binary annotation. */
  public static BinaryAnnotation create(String key, String value,
      @Nullable Endpoint endpoint) {
    return create(key, value.getBytes(UTF_8), AnnotationType.STRING, endpoint);
  }

  public static BinaryAnnotation create(String key, byte[] value, AnnotationType type,
      @Nullable Endpoint endpoint) {
    return new BinaryAnnotation(key, value, type, endpoint);
  }

  public final String key; // required
  public final byte[] value; // required
  /**
   * 
   * @see AnnotationType
   */
  public final AnnotationType type; // required
  /**
   * The host that recorded tag, which allows you to differentiate between
   * multiple tags with the same key. There are two exceptions to this.
   * 
   * When the key is CLIENT_ADDR or SERVER_ADDR, host indicates the source or
   * destination of an RPC. This exception allows zipkin to display network
   * context of uninstrumented services, or clients such as web browsers.
   */
  public final Endpoint host; // optional

  BinaryAnnotation(String key, byte[] value, AnnotationType type, @Nullable Endpoint host) {
    this.key = checkNotBlank(key, "Null or blank key");
    this.value = checkNotNull(value, "Null value");
    this.type = type;
    this.host = host;
  }

  public String getKey() {
    return this.key;
  }

  public byte[] getValue() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof BinaryAnnotation) {
      BinaryAnnotation that = (BinaryAnnotation) o;
      return (this.key.equals(that.key))
          && (Arrays.equals(this.value, that.value))
          && (this.type.equals(that.type))
          && equal(this.host, that.host);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= key.hashCode();
    h *= 1000003;
    h ^= Arrays.hashCode(value);
    h *= 1000003;
    h ^= type.hashCode();
    h *= 1000003;
    h ^= (host == null) ? 0 : host.hashCode();
    return h;
  }
}


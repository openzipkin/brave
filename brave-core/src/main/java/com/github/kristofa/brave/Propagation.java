package com.github.kristofa.brave;

import com.github.kristofa.brave.internal.B3Propagation;
import com.github.kristofa.brave.internal.Nullable;

/**
 * Injects and extracts {@link SpanId span identifiers} sent in-band across process boundaries.
 *
 * <p>Implementations usually wrap Http, Messaging or RPC headers.
 *
 * @param <K> Usually, but not always a String
 * @since 3.17
 */
public interface Propagation<K> {

  interface Factory {
    Factory B3 = new Factory() {
      @Override public <K> Propagation<K> create(KeyFactory<K> keyFactory) {
        return B3Propagation.create(keyFactory);
      }
    };

    <K> Propagation<K> create(KeyFactory<K> keyFactory);
  }

  /** Creates keys for use in propagated contexts */
  interface KeyFactory<K> {
    KeyFactory<String> STRING = name -> name;

    K create(String name);
  }

  /** Replaces a propagated key with the given value */
  interface Setter<C, K> {
    void put(C carrier, K key, String value);
  }

  /**
   * @param setter invoked for each propagation key to add.
   */
  <C> Injector<C> injector(Setter<C, K> setter);

  /**
   * @param <C> carrier of propagated trace data. For example, a http request or message.
   */
  interface Injector<C> {
    /**
     * Calls the setter for each key to send downstream.
     *
     * @param spanId possibly null span identifiers. Null means unsampled.
     * @param carrier holds the propagated keys. For example, an outgoing message or http request.
     */
    void injectSpanId(@Nullable SpanId spanId, C carrier);
  }

  /** Gets the first value of the given propagation key or returns null */
  interface Getter<C, K> {
    @Nullable String get(C carrier, K key);
  }

  /**
   * @param getter invoked for each propagation key to get.
   */
  <C> Extractor<C> extractor(Getter<C, K> getter);

  /**
   * @param <C> carrier of propagated trace data. For example, a http request or message.
   */
  interface Extractor<C> {
    /**
     * Calls the getter for each key needed to create trace data. Returns {@link TraceData#EMPTY} if
     * absent or unreadable.
     *
     * @param carrier holds the propagated keys. For example, an incoming message or http request.
     */
    TraceData extractTraceData(C carrier);
  }
}

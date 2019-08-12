/*
 * Copyright 2013-2019 The OpenZipkin Authors
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
package brave.propagation;

import brave.internal.Nullable;
import java.util.List;

import static brave.propagation.Propagation.KeyFactory.STRING;

/**
 * Injects and extracts {@link TraceContext trace identifiers} as text into carriers that travel
 * in-band across process boundaries. Identifiers are often encoded as messaging or RPC request
 * headers.
 *
 * <h3>Propagation example: Http</h3>
 *
 * <p>When using http, the carrier of propagated data on both the client (injector) and server
 * (extractor) side is usually an http request. Propagation is usually implemented via library-
 * specific request interceptors, where the client-side injects span identifiers and the server-side
 * extracts them.
 *
 * @param <K> Usually, but not always a String
 */
public interface Propagation<K> {
  Propagation<String> B3_STRING = B3Propagation.FACTORY.create(STRING);
  Propagation<String> B3_SINGLE_STRING = B3SinglePropagation.FACTORY.create(STRING);

  abstract class Factory {
    /**
     * Does the propagation implementation support sharing client and server span IDs. For example,
     * should an RPC server span share the same identifiers extracted from an incoming request?
     *
     * In usual <a href="https://github.com/apache/incubator-zipkin-b3-propagation">B3
     * Propagation</a>, the parent span ID is sent across the wire so that the client and server can
     * share the same identifiers. Other propagation formats, like <a href="https://github.com/TraceContext/tracecontext-spec">trace-context</a>
     * only propagate the calling trace and span ID, with an assumption that the receiver always
     * starts a new child span. When join is supported, you can assume that when {@link
     * TraceContext#parentId() the parent span ID} is null, you've been propagated a root span. When
     * join is not supported, you must always fork a new child.
     */
    public boolean supportsJoin() {
      return false;
    }

    public boolean requires128BitTraceId() {
      return false;
    }

    public abstract <K> Propagation<K> create(KeyFactory<K> keyFactory);

    /**
     * Decorates the input such that it can propagate extra data, such as a timestamp or a carrier
     * for extra fields.
     *
     * <p>Implementations are responsible for data scoping, if relevant. For example, if only
     * global configuration is present, it could suffice to simply ensure that data is present. If
     * data is span-scoped, an implementation might compare the context to its last span ID, copying
     * on write or otherwise to ensure writes to one context don't affect another.
     *
     * <p>Implementations should be idempotent, returning the same instance instead of re-applying
     * change.
     *
     * @see TraceContext#extra()
     */
    public TraceContext decorate(TraceContext context) {
      return context;
    }
  }

  /** Creates keys for use in propagated contexts */
  interface KeyFactory<K> {
    KeyFactory<String> STRING = new KeyFactory<String>() { // retrolambda no likey
      @Override public String create(String name) {
        return name;
      }

      @Override public String toString() {
        return "StringKeyFactory{}";
      }
    };

    K create(String name);
  }

  /** Replaces a propagated key with the given value */
  interface Setter<C, K> {
    void put(C carrier, K key, String value);
  }

  /**
   * The propagation fields defined. If your carrier is reused, you should delete the fields here
   * before calling {@link Setter#put(Object, Object, String)}.
   *
   * <p>For example, if the carrier is a single-use or immutable request object, you don't need to
   * clear fields as they couldn't have been set before. If it is a mutable, retryable object,
   * successive calls should clear these fields first.
   *
   * <p><em>Note:</em> If your implementation carries "extra fields", such as correlation IDs, do
   * not return the names of those fields here. If you do, they will be deleted, which can interfere
   * with user headers.
   */
  // The use cases of this are:
  // * allow pre-allocation of fields, especially in systems like gRPC Metadata
  // * allow a single-pass over an iterator (ex OpenTracing has no getter in TextMap)
  List<K> keys();

  /**
   * Replaces a propagated field with the given value. Saved as a constant to avoid runtime
   * allocations.
   *
   * For example, a setter for an {@link java.net.HttpURLConnection} would be the method reference
   * {@link java.net.HttpURLConnection#addRequestProperty(String, String)}
   *
   * @param setter invoked for each propagation key to add.
   */
  <C> TraceContext.Injector<C> injector(Setter<C, K> setter);

  /** Gets the first value of the given propagation key or returns null */
  interface Getter<C, K> {
    @Nullable String get(C carrier, K key);
  }

  /**
   * @param getter invoked for each propagation key to get.
   */
  <C> TraceContext.Extractor<C> extractor(Getter<C, K> getter);
}

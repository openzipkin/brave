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
package brave.propagation;

import brave.Request;
import brave.baggage.BaggagePropagation;
import brave.internal.Nullable;
import brave.internal.propagation.StringPropagationAdapter;
import java.util.List;

/**
 * Injects and extracts {@link TraceContext trace identifiers} as text into requests that travel
 * in-band across process boundaries. Identifiers are often encoded as messaging or RPC request
 * headers.
 *
 * <h3>Propagation example: HTTP</h3>
 *
 * <p>When using HTTP, the client (injector) and server (extractor) use request headers. The client
 * {@linkplain TraceContext.Injector#inject injects} the trace context into headers before the
 * request is sent to the server. The server {@linkplain TraceContext.Extractor#extract extracts} a
 * trace context from these headers before processing the request.
 *
 * @param <K> Deprecated except when a {@link String}.
 */
public interface Propagation<K> {
  Propagation<String> B3_STRING = B3Propagation.get();
  /**
   * @deprecated Since 5.9, use {@link B3Propagation#newFactoryBuilder()} to control inject formats.
   */
  @Deprecated Propagation<String> B3_SINGLE_STRING = B3SinglePropagation.FACTORY.get();

  abstract class Factory {
    /**
     * Does the propagation implementation support sharing client and server span IDs. For example,
     * should an RPC server span share the same identifiers extracted from an incoming request?
     *
     * In usual <a href="https://github.com/openzipkin/b3-propagation">B3 Propagation</a>, the
     * parent span ID is sent across the wire so that the client and server can share the same
     * identifiers. Other propagation formats, like <a href="https://github.com/w3c/trace-context">trace-context</a>
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

    /**
     * This is deprecated: end users and instrumentation should never call this, and instead use
     * {@link #get()}.
     *
     * <h3>Implementation advice</h3>
     * This is deprecated, but abstract. This means those implementing custom propagation formats
     * will have to implement this until it is removed in Brave 6. If you are able to use a tool
     * such as "maven-shade-plugin", consider using {@link StringPropagationAdapter}.
     *
     * @param <K> Deprecated except when a {@link String}.
     * @see KeyFactory#STRING
     * @deprecated Since 5.12, use {@link #get()}
     */
    @Deprecated
    public abstract <K> Propagation<K> create(KeyFactory<K> keyFactory);

    /**
     * Returns a possibly cached propagation instance.
     *
     * <p>Implementations should override and implement this method directly.
     *
     * @since 5.12
     */
    public Propagation<String> get() {
      return create(KeyFactory.STRING);
    }

    /**
     * Decorates the input such that it can propagate extra state, such as a timestamp or baggage.
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

  /**
   * @deprecated since 5.12 non-string keys are no longer supported
   */
  @Deprecated
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

  /**
   * Replaces a propagated key with the given value.
   *
   * @param <R> Usually, but not always, an instance of {@link Request}.
   * @param <K> Deprecated except when a {@link String}.
   */
  interface Setter<R, K> {
    void put(R request, K key, String value);
  }

  /**
   * The propagation fields defined. If your request is reused, you should delete the fields here
   * before calling {@link Setter#put(Object, Object, String)}.
   *
   * <p>For example, if the request is a single-use or immutable request object, you don't need to
   * clear fields as they couldn't have been set before. If it is a mutable, retryable object,
   * successive calls should clear these fields first.
   *
   * <p><em>Note:</em> Depending on the format, keys returned may not all be mandatory.
   *
   * <p><em>Note:</em> If your implementation carries baggage, such as correlation IDs, do not
   * return the names of those fields here. If you do, they will be deleted, which can interfere
   * with user headers.
   *
   * @see BaggagePropagation#allKeyNames(Propagation)
   */
  // The use cases of this are:
  // * allow pre-allocation of fields, especially in systems like gRPC Metadata
  // * allow a single-pass over an iterator (ex OpenTracing has no getter in TextMap)
  // * detection of if a context is likely to be present in a request object
  List<K> keys();

  /**
   * Replaces a propagated field with the given value. Saved as a constant to avoid runtime
   * allocations.
   *
   * For example, a setter for an {@link java.net.HttpURLConnection} would be the method reference
   * {@link java.net.HttpURLConnection#addRequestProperty(String, String)}
   *
   * @param setter invoked for each propagation key to add.
   * @param <R> Usually, but not always, an instance of {@link Request}.
   */
  <R> TraceContext.Injector<R> injector(Setter<R, K> setter);

  /**
   * Gets the first value of the given propagation key or returns {@code null}.
   *
   * @param <R> Usually, but not always, an instance of {@link Request}.
   * @param <K> Deprecated except when a {@link String}.
   */
  interface Getter<R, K> {
    @Nullable String get(R request, K key);
  }

  /**
   * @param getter invoked for each propagation key to get.
   * @param <R> Usually, but not always, an instance of {@link Request}.
   */
  <R> TraceContext.Extractor<R> extractor(Getter<R, K> getter);
}

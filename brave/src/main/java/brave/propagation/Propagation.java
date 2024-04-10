/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.propagation;

import brave.Request;
import brave.Span.Kind;
import brave.baggage.BaggagePropagation;
import brave.internal.Nullable;
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
 * @param <K> Retained for compatibility with pre Brave 6.0, but always String.
 * @since 4.0
 */
// The generic type parameter K is always <String>. Even if the deprecated methods are removed in
// Brave 6.0. This is to avoid a compilation break and revlock.
public interface Propagation<K> {
  /**
   * Defaults B3 formats based on {@link Request} type. When not a {@link Request} (e.g. in-process
   * messaging), this uses {@link B3Propagation.Format#SINGLE_NO_PARENT}.
   *
   * @since 4.0
   */
  Propagation<String> B3_STRING = B3Propagation.get();
  /**
   * Implements the propagation format described in {@link B3SingleFormat}.
   */
  Propagation<String> B3_SINGLE_STRING = B3SinglePropagation.FACTORY.get();

  /** @since 4.0 */
  abstract class Factory {
    /**
     * Does the propagation implementation support sharing client and server span IDs. For example,
     * should an RPC server span share the same identifiers extracted from an incoming request?
     *
     * <p>In usual <a href="https://github.com/openzipkin/b3-propagation">B3 Propagation</a>, the
     * parent span ID is sent across the wire so that the client and server can share the same
     * identifiers. Other propagation formats, like <a href="https://github.com/w3c/trace-context">trace-context</a>
     * only propagate the calling trace and span ID, with an assumption that the receiver always
     * starts a new child span. When join is supported, you can assume that when {@link
     * TraceContext#parentId() the parent span ID} is null, you've been propagated a root span. When
     * join is not supported, you must always fork a new child.
     *
     * @since 4.7
     */
    public boolean supportsJoin() {
      return false;
    }

    /**
     * Returns {@code true} if the implementation cannot use 64-bit trace IDs.
     *
     * @since 4.9
     */
    public boolean requires128BitTraceId() {
      return false;
    }

    /**
     * @deprecated end users and instrumentation should never call this, and instead use
     * {@link #get()}. This will be removed in Brave 7, to allow users to transition without revlock
     * upgrading to Brave 6.
     */
    @Deprecated public <K> Propagation<K> create(KeyFactory<K> unused) {
      // In Brave 5.12, this was abstract, but not used: `get()` dispatched
      // to this. Brave 5.18 implemented this with the below exception to force
      // `get()` to be overridden. Doing so allows us to make `get()` abstract
      // in Brave 6.0. Then, this can be safely removed in Brave 7.0 without a
      // revlock.
      throw new UnsupportedOperationException("This was replaced with PropagationFactory.get() in Brave 5.12");
    }

    /**
     * Returns a possibly cached propagation instance.
     *
     * @since 5.12
     */
    public Propagation<String> get() {
      // In Brave 5.12, this dispatched to the deprecated abstract method
      // `create()`. In Brave 5.18, we throw an exception instead to ensure it
      // is implemented prior to Brave 6.0 making this abstract.
      throw new UnsupportedOperationException("As of Brave 5.18, you must implement PropagationFactory.get()");
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
     * @since 4.9
     */
    public TraceContext decorate(TraceContext context) {
      return context;
    }
  }

  /**
   * @since 4.0
   * @deprecated since 5.12 non-string keys are no longer supported. This will be removed in Brave
   * 7, to allow users to transition without revlock upgrading to Brave 6.
   */
  @Deprecated
  interface KeyFactory<K> {
    KeyFactory<String> STRING = new KeyFactory<String>() {
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
   * @param <K> Retained for compatibility with pre Brave 6.0, but always String.
   * @see RemoteSetter
   * @since 4.0
   */
  interface Setter<R, K> {
    void put(R request, K key, String value);
  }

  /**
   * Returns the key names used for propagation of the current span. The result can be cached in the
   * same scope as the propagation instance.
   *
   * <p>This method exists to support remote propagation of trace IDs:
   * <ul>
   *   <li>To generate constants for all key names. ex. gRPC Metadata.Key</li>
   *   <li>To iterate fields when missing a get field by name function. ex. OpenTracing TextMap</li>
   *   <li>Detection of if a context is likely to be present in a request object</li>
   *   <li>To clear trace ID fields on re-usable requests. ex. JMS message</li>
   * </ul>
   *
   * <h3>Notes</h3>
   * <p>Depending on the format, keys returned may not all be mandatory.
   *
   * <p>If your implementation carries baggage, such as correlation IDs, do not return the names of
   * those fields here. If you do, they will be deleted, which can interfere with user headers.
   * Instead, use {@link BaggagePropagation} which returns those names in {@link BaggagePropagation#allKeyNames(Propagation)}.
   *
   * <h3>Edge-cases</h3>
   * When a request is a single-use or immutable request object, there are no known edge cases to
   * consider. Mutable, retryable objects such as messaging headers should be careful about some
   * edge cases:
   *
   * <p>When multiple headers are used for trace identifiers, ex {@link B3Propagation.Format#MULTI},
   * producers should be careful to clear fields here before calling {@link TraceContext.Injector#inject(TraceContext, Object)}
   * when the input is a new root span. Otherwise, a stale {@link TraceContext#parentIdString()}
   * could be left in the headers and be mistaken for a missing root span.
   *
   * <p>Headers here should be cleared when invoking listeners in
   * {@linkplain CurrentTraceContext.Scope scope}. Doing so prevents precedence rules that prefer
   * the trace context in message headers from overriding the current span. Doing so would place any
   * follow-up activity in the wrong spot in the trace tree.
   *
   * @see BaggagePropagation#allKeyNames(Propagation)
   * @since 4.0
   */
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
   * @see RemoteSetter
   * @since 4.0
   */
  <R> TraceContext.Injector<R> injector(Setter<R, K> setter);

  /**
   * Gets the first value of the given propagation key or returns {@code null}.
   *
   * @param <R> Usually, but not always, an instance of {@link Request}.
   * @param <K> Retained for compatibility with pre Brave 6.0, but always String.
   * @see RemoteGetter
   * @since 4.0
   */
  interface Getter<R, K> {
    @Nullable String get(R request, K key);
  }

  /**
   * Used as an input to {@link Propagation#injector(Setter)} inject the {@linkplain TraceContext
   * trace context} and any {@linkplain BaggagePropagation baggage} as propagated fields.
   *
   * @param <R> usually {@link Request}, such as an HTTP server request or message
   * @see RemoteGetter
   * @since 5.12
   */
  // this is not `R extends Request` for APIs like OpenTracing that know the remote kind, but don't
  // implement the `Request` abstraction
  interface RemoteSetter<R> extends Setter<R, String> {
    /**
     * The only valid options are {@link Kind#CLIENT}, {@link Kind#PRODUCER}, and {@link
     * Kind#CONSUMER}.
     *
     * @see Request#spanKind()
     * @since 5.12
     */
    Kind spanKind();

    /**
     * Replaces a propagation field with the given value.
     *
     * <p><em>Note</em>: Implementations attempt to overwrite all values. This means that when the
     * caller is encoding multi-value (comma-separated list) HTTP header, they MUST join all values
     * on comma into a single string.
     *
     * @param request see {@link #<R>}
     * @param fieldName typically a header name
     * @param value non-{@code null} value to replace any values with
     * @see RemoteGetter
     * @since 5.12
     */
    @Override void put(R request, String fieldName, String value);
  }

  /**
   * @param getter invoked for each propagation key to get.
   * @param <R> Usually, but not always, an instance of {@link Request}.
   * @see RemoteGetter
   * @since 4.0
   */
  <R> TraceContext.Extractor<R> extractor(Getter<R, K> getter);

  /**
   * Used as an input to {@link Propagation#extractor(Getter)} extract the {@linkplain TraceContext
   * trace context} and any {@linkplain BaggagePropagation baggage} from propagated fields.
   *
   * @param <R> usually {@link Request}, such as an HTTP server request or message
   * @see RemoteSetter
   * @since 5.12
   */
  // this is not `R extends Request` for APIs like OpenTracing that know the remote kind, but don't
  // implement the `Request` abstraction
  interface RemoteGetter<R> extends Getter<R, String> {
    /**
     * The only valid options are {@link Kind#SERVER}, {@link Kind#PRODUCER}, and {@link
     * Kind#CONSUMER}.
     *
     * @see Request#spanKind()
     * @since 5.12
     */
    Kind spanKind();

    /**
     * Gets the propagation field as a single value.
     *
     * <p><em>Note</em>: HTTP only permits multiple header fields with the same name when the
     * format
     * is a comma-separated list. An HTTP implementation of this method will assume presence of
     * multiple values is valid and join them with a comma. See <a href="https://tools.ietf.org/html/rfc7230#section-3.2.2">RFC
     * 7230</a> for more.
     *
     * @param request see {@link #<R>}
     * @param fieldName typically a header name
     * @return the value of the field or {@code null}
     * @since 5.12
     */
    @Nullable @Override String get(R request, String fieldName);
  }
}

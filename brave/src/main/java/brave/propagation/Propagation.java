package brave.propagation;

import brave.TraceContext;
import brave.internal.Nullable;

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
 * <h3>Relationship to OpenTracing</h3>
 *
 * <p>OpenTracing's Tracer type has methods to inject or extract a trace context from a carrier.
 * While naming is similar there are some important differences:
 *
 * <pre><ul>
 *   <li>Injection and Extraction types here access data in a carrier by key</li>
 *   <li>Input and output types reuse Brave's {@link TraceContext} and {@link TraceContext}</li>
 *   <li>Binary propagation is out of scope</li>
 * </ul></pre>
 *
 * <h4>Key-based access</h4>
 *
 * <p>OpenTracing's TextMap requires you to iterate all keys to find headers such as 'B3-TraceId'.
 * Particularly in gRPC, this is a problem as the keyspace is shared between ascii and binary data.
 * Moreover, Brave doesn't support arbitrary propagation, so it will always know the keys it needs.
 *
 * <h4>Binary propagation is out of scope</h4>
 *
 * In practice, there has been only one usage of Brave's binary propagation form (cassandra). It
 * isn't used enough to warrant being first class. Moreover, future header formats are likely to be
 * a single-header with a delimited text value. The impact is that those doing binary encoding with
 * {@link TraceContext} directly will need to continue doing that.
 *
 * @param <K> Usually, but not always a String
 * @since 3.17
 */
public interface Propagation<K> {
  Propagation<String> B3_STRING =
      Propagation.Factory.B3.create(Propagation.KeyFactory.STRING, false);

  interface Factory {
    Factory B3 = new Factory() {
      @Override public <K> Propagation<K> create(KeyFactory<K> keyFactory, boolean traceId128Bit) {
        return B3Propagation.create(keyFactory, traceId128Bit);
      }
    };

    <K> Propagation<K> create(KeyFactory<K> keyFactory, boolean traceId128Bit);
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
  <C> TraceContextInjector<C> injector(Setter<C, K> setter);

  /** Gets the first value of the given propagation key or returns null */
  interface Getter<C, K> {
    @Nullable String get(C carrier, K key);
  }

  /**
   * @param getter invoked for each propagation key to get.
   */
  <C> TraceContextExtractor<C> extractor(Getter<C, K> getter);
}

package brave.propagation;

import brave.internal.Nullable;
import java.util.List;

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
  Propagation<String> B3_STRING = Propagation.Factory.B3.create(Propagation.KeyFactory.STRING);

  interface Factory {
    Factory B3 = B3Propagation::create;

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

  /** The propagation fields defined */
  // The use cases of this are:
  // * allow pre-allocation of fields, especially in systems like gRPC Metadata
  // * allow a single-pass over an iterator (ex OpenTracing has no getter in TextMap)
  List<K> keys();

  /**
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

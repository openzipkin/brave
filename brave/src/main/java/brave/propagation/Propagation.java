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
  Propagation<String> B3_STRING = B3Propagation.FACTORY.create(Propagation.KeyFactory.STRING);

  abstract class Factory {
    /**
     * Does the propagation implementation support sharing client and server span IDs. For example,
     * should an RPC server span share the same identifiers extracted from an incoming request?
     *
     * In usual <a href="https://github.com/openzipkin/b3-propagation">B3 Propagation</a>, the
     * parent span ID is sent across the wire so that the client and server can share the same
     * identifiers. Other propagation formats, like <a href="https://github.com/TraceContext/tracecontext-spec">trace-context</a>
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

    // added here because Propagation is an interface and adding this method there would break api
    public <C> MutableTraceContext.Extractor<C> extractor(Getter<C, String> getter) {
      return extractor(KeyFactory.STRING, getter);
    }

    /**
     * @param getter invoked for each propagation key to get.
     */
    // added here because Propagation is an interface and adding this method there would break api
    public <C, K> MutableTraceContext.Extractor<C> extractor(KeyFactory<K> keyFactory,
        Getter<C, K> getter) {
      TraceContext.Extractor<C> delegate = create(keyFactory).extractor(getter);
      return new MutableTraceContext.Extractor<C>() {

        @Override public void extract(C carrier, MutableTraceContext state) {
          MutableTraceContext.copy(delegate.extract(carrier), state);
        }

        @Override public String toString() {
          return delegate.toString();
        }
      };
    }

    /**
     * Decorates the input such that it can propagate extra data, such as a timestamp or a carrier
     * for extra fields.
     *
     * <p>Implementations are responsible for data scoping, if relevant. For example, if only
     * global configuration is present, it could suffice to simply ensure that data is present. If
     * data is span-scoped, an implementation might compare the context to its last span ID, copying
     * on write or otherwise to ensure writes to one context don't affect another.
     *
     * <p>Implementations should be idempotent, returning instead of re-applying change. By default
     * {@linkplain #isDecorated(MutableTraceContext)} is used for this.
     *
     * @see TraceContext#extra()
     */
    public void decorate(MutableTraceContext mutableContext) {
      if (isDecorated(mutableContext)) return;

      // Guard to avoid stack overflow dispatching to a method that dispatches to this
      if (overridesDecorateTraceContext) {
        TraceContext replaced = decorate(mutableContext.toTraceContext());
        mutableContext.traceIdHigh = replaced.traceIdHigh;
        mutableContext.traceId = replaced.traceId;
        mutableContext.parentId = replaced.parentId;
        mutableContext.spanId = replaced.spanId;
        mutableContext.flags = replaced.flags;
        mutableContext.extra = replaced.extra;
      }
    }

    /** Return true if the input is already decorated for this trace and span ID */
    protected boolean isDecorated(MutableTraceContext mutableContext) {
      return false;
    }

    /**
     * Like {@link #decorate(MutableTraceContext)}, except for an existing trace context. Used by
     * {@link brave.Tracer#toSpan(TraceContext)} which allows re-use of existing trace IDs.
     *
     * <p>Implementations should be idempotent, returning the same instance where needed. By
     * default {@linkplain #isDecorated(TraceContext)} is used for this.
     */
    public TraceContext decorate(TraceContext context) {
      if (isDecorated(context)) return context;
      // Guard to avoid stack overflow dispatching to a method that dispatches to this
      if (overridesDecorateMutableTraceContext) {
        MutableTraceContext mutable = MutableTraceContext.create(context);
        decorate(mutable);
        return mutable.toTraceContext();
      }
      return context;
    }

    /** Return true if the input is already decorated for this trace and span ID */
    protected boolean isDecorated(TraceContext context) {
      return false;
    }

    final boolean overridesDecorateTraceContext, overridesDecorateMutableTraceContext;

    /** Uses reflection to backport decoration mechanisms */
    public Factory() {
      boolean overridesDecorateTraceContext = false, overridesDecorateMutableTraceContext = false;
      try {
        overridesDecorateTraceContext =
            getClass().getMethod("decorate", TraceContext.class).getDeclaringClass()
                != Factory.class;
        overridesDecorateMutableTraceContext =
            getClass().getMethod("decorate", MutableTraceContext.class).getDeclaringClass()
                != Factory.class;
      } catch (NoSuchMethodException ignored) {
      }
      this.overridesDecorateTraceContext = overridesDecorateTraceContext;
      this.overridesDecorateMutableTraceContext = overridesDecorateMutableTraceContext;
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

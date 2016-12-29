package brave.propagation;

import brave.TraceContext;
import brave.internal.HexCodec;
import brave.internal.Internal;
import brave.internal.Platform;

import static brave.internal.HexCodec.lowerHexToUnsignedLong;

/**
 * Implements <a href="https://github.com/openzipkin/b3-propagation">B3 Propagation</a>
 */
public final class B3Propagation<K> implements Propagation<K> {

  public static <K> B3Propagation<K> create(KeyFactory<K> keyFactory, boolean traceId128) {
    return new B3Propagation<>(keyFactory, traceId128);
  }

  /**
   * 128 or 64-bit trace ID lower-hex encoded into 32 or 16 characters (required)
   */
  static final String TRACE_ID_NAME = "X-B3-TraceId";
  /**
   * 64-bit span ID lower-hex encoded into 16 characters (required)
   */
  static final String SPAN_ID_NAME = "X-B3-SpanId";
  /**
   * 64-bit parent span ID lower-hex encoded into 16 characters (absent on root span)
   */
  static final String PARENT_SPAN_ID_NAME = "X-B3-ParentSpanId";
  /**
   * "1" means report this span to the tracing system, "0" means do not. (absent means defer the
   * decision to the receiver of this header).
   */
  static final String SAMPLED_NAME = "X-B3-Sampled";

  final K traceIdKey;
  final K spanIdKey;
  final K parentSpanIdKey;
  final K sampledKey;
  final boolean traceId128Bit;

  B3Propagation(KeyFactory<K> keyFactory, boolean traceId128Bit) {
    this.traceIdKey = keyFactory.create(TRACE_ID_NAME);
    this.spanIdKey = keyFactory.create(SPAN_ID_NAME);
    this.parentSpanIdKey = keyFactory.create(PARENT_SPAN_ID_NAME);
    this.sampledKey = keyFactory.create(SAMPLED_NAME);
    this.traceId128Bit = traceId128Bit;
  }

  @Override public <C> TraceContextInjector<C> injector(Setter<C, K> setter) {
    if (setter == null) throw new NullPointerException("setter == null");
    return new B3Injector<>(this, setter);
  }

  static final class B3Injector<C, K> implements TraceContextInjector<C> {
    final B3Propagation<K> propagation;
    final Setter<C, K> setter;

    B3Injector(B3Propagation<K> propagation, Setter<C, K> setter) {
      this.propagation = propagation;
      this.setter = setter;
    }

    @Override public void inject(TraceContext traceContext, C carrier) {
      setter.put(carrier, propagation.traceIdKey, traceContext.traceIdString());
      setter.put(carrier, propagation.spanIdKey, HexCodec.toLowerHex(traceContext.spanId()));
      if (traceContext.parentId() != null) {
        setter.put(carrier, propagation.parentSpanIdKey,
            HexCodec.toLowerHex(traceContext.parentId()));
      }
      if (traceContext.sampled() != null) {
        setter.put(carrier, propagation.sampledKey, traceContext.sampled() ? "1" : "0");
      }
    }
  }

  @Override public <C> TraceContextExtractor<C> extractor(Getter<C, K> getter) {
    if (getter == null) throw new NullPointerException("getter == null");
    return new B3Extractor(this, getter, traceId128Bit);
  }

  static final class B3Extractor<C, K> implements TraceContextExtractor<C> {
    final B3Propagation<K> propagation;
    final Getter<C, K> getter;
    final boolean traceId128Bit;

    B3Extractor(B3Propagation<K> propagation, Getter<C, K> getter, boolean traceId128Bit) {
      this.propagation = propagation;
      this.getter = getter;
      this.traceId128Bit = traceId128Bit;
    }

    @Override public TraceContext extract(C carrier) {
      if (carrier == null) throw new NullPointerException("carrier == null");

      String sampledString = getter.get(carrier, propagation.sampledKey);
      // Official sampled value is 1, though some old instrumentation send true
      Boolean sampled = sampledString != null
          ? sampledString.equals("1") || sampledString.equalsIgnoreCase("true")
          : null;

      String traceIdString = getter.get(carrier, propagation.traceIdKey);
      TraceContext.Builder result = Internal.instance.newTraceContextBuilder()
          .sampled(sampled);
      if (traceIdString != null) {
        result.traceIdHigh(
            traceIdString.length() == 32 ? lowerHexToUnsignedLong(traceIdString, 0) : 0);
        result.traceId(lowerHexToUnsignedLong(traceIdString));
      } else {
        // If no trace ids are set, generate a trace ID and span ID rather than deal with absence of
        // them elsewhere in the code.
        long spanId = Platform.get().randomLong();
        result.traceIdHigh(traceId128Bit ? Platform.get().randomLong() : 0L);
        result.traceId(spanId);
        return result.spanId(spanId).build();
      }

      String spanIdString = getter.get(carrier, propagation.spanIdKey);
      result.spanId(lowerHexToUnsignedLong(spanIdString));

      String parentSpanIdString = getter.get(carrier, propagation.parentSpanIdKey);
      if (parentSpanIdString != null) {
        result.parentId(lowerHexToUnsignedLong(parentSpanIdString));
      }

      return result.shared(sampled != null && sampled).build();
    }
  }
}

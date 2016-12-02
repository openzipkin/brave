package com.github.kristofa.brave.internal;

import com.github.kristofa.brave.Propagation;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.TraceData;

import static com.github.kristofa.brave.IdConversion.convertToLong;
import static com.github.kristofa.brave.IdConversion.convertToString;
import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Implements <a href="https://github.com/openzipkin/b3-propagation">B3 Propagation</a>
 */
public final class B3Propagation<K> implements Propagation<K> {

  public static <K> B3Propagation<K> create(KeyFactory<K> keyFactory) {
    return new B3Propagation<K>(keyFactory);
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

  B3Propagation(KeyFactory<K> keyFactory) {
    this.traceIdKey = keyFactory.create(TRACE_ID_NAME);
    this.spanIdKey = keyFactory.create(SPAN_ID_NAME);
    this.parentSpanIdKey = keyFactory.create(PARENT_SPAN_ID_NAME);
    this.sampledKey = keyFactory.create(SAMPLED_NAME);
  }

  @Override public <C> Injector<C> injector(Setter<C, K> setter) {
    return new B3Injector<C, K>(this, setter);
  }

  static final class B3Injector<C, K> implements Injector<C> {
    private final B3Propagation<K> propagation;
    private final Setter<C, K> setter;

    B3Injector(B3Propagation<K> propagation, Setter<C, K> getter) {
      this.propagation = propagation;
      this.setter = checkNotNull(getter, "setter");
    }

    @Override public void injectSpanId(@Nullable SpanId spanId, C carrier) {
      if (spanId == null) {
        setter.put(carrier, propagation.sampledKey, "0");
      } else {
        setter.put(carrier, propagation.traceIdKey, spanId.traceIdString());
        setter.put(carrier, propagation.spanIdKey, convertToString(spanId.spanId));
        if (spanId.nullableParentId() != null) {
          setter.put(carrier, propagation.parentSpanIdKey, convertToString(spanId.parentId));
        }
        setter.put(carrier, propagation.sampledKey, "1");
      }
    }
  }

  @Override public <C> Extractor<C> extractor(Getter<C, K> getter) {
    return new B3Extractor(this, getter);
  }

  static final class B3Extractor<C, K> implements Extractor<C> {
    private final B3Propagation<K> propagation;
    private final Getter<C, K> getter;

    B3Extractor(B3Propagation<K> propagation, Getter<C, K> getter) {
      this.propagation = propagation;
      this.getter = checkNotNull(getter, "getter");
    }

    @Override public TraceData extractTraceData(C carrier) {
      checkNotNull(carrier, "carrier");
      String traceId = getter.get(carrier, propagation.traceIdKey);
      String spanId = getter.get(carrier, propagation.spanIdKey);
      String parentSpanId = getter.get(carrier, propagation.parentSpanIdKey);
      String sampled = getter.get(carrier, propagation.sampledKey);

      // Official sampled value is 1, though some old instrumentation send true
      Boolean parsedSampled = sampled != null
          ? sampled.equals("1") || sampled.equalsIgnoreCase("true")
          : null;

      if (traceId != null && spanId != null) {
        return TraceData.create(getSpanId(traceId, spanId, parentSpanId, parsedSampled));
      } else if (parsedSampled == null) {
        return TraceData.EMPTY;
      } else if (parsedSampled.booleanValue()) {
        // Invalid: The caller requests the trace to be sampled, but didn't pass IDs
        return TraceData.EMPTY;
      } else {
        return TraceData.NOT_SAMPLED;
      }
    }
  }

  static SpanId getSpanId(String traceId, String spanId, String parentSpanId, Boolean sampled) {
    return SpanId.builder()
        .traceIdHigh(traceId.length() == 32 ? convertToLong(traceId, 0) : 0)
        .traceId(convertToLong(traceId))
        .spanId(convertToLong(spanId))
        .sampled(sampled)
        .parentId(parentSpanId == null ? null : convertToLong(parentSpanId)).build();
  }
}

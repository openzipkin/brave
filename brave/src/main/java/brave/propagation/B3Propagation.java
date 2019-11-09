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

import brave.Request;
import brave.Span;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static brave.propagation.B3SingleFormat.parseB3SingleFormat;
import static brave.propagation.B3SingleFormat.writeB3SingleFormat;
import static brave.propagation.B3SingleFormat.writeB3SingleFormatWithoutParentId;
import static java.util.Arrays.asList;

/**
 * Implements <a href="https://github.com/openzipkin/b3-propagation">B3 Propagation</a>
 */
public final class B3Propagation<K> implements Propagation<K> {
  /** Describes the formats used to inject headers. */
  enum Format {
    /** The trace context is encoded with a several fields prefixed with "x-b3-". */
    MULTI,
    /** The trace context is encoded with {@link B3SingleFormat#writeB3SingleFormat(TraceContext)}. */
    SINGLE,
    /** The trace context is encoded with {@link B3SingleFormat#writeB3SingleFormatWithoutParentId(TraceContext)}. */
    SINGLE_NO_PARENT
  }

  public static final Propagation.Factory FACTORY = newFactoryBuilder().build();

  public static FactoryBuilder newFactoryBuilder() {
    return new FactoryBuilder();
  }

  /**
   * Defaults to {@link Format#MULTI} for client/server spans and {@link Format#SINGLE_NO_PARENT}
   * for messaging. Non-request spans default to {@link Format#MULTI}.
   */
  public static final class FactoryBuilder {
    Format injectFormat = Format.MULTI;
    final EnumMap<Span.Kind, Format[]> kindToInjectFormats = new EnumMap<>(Span.Kind.class);

    FactoryBuilder() {
      kindToInjectFormats.put(Span.Kind.CLIENT, new Format[] {Format.MULTI});
      kindToInjectFormats.put(Span.Kind.SERVER, new Format[] {Format.MULTI});
      kindToInjectFormats.put(Span.Kind.PRODUCER, new Format[] {Format.SINGLE_NO_PARENT});
      kindToInjectFormats.put(Span.Kind.CONSUMER, new Format[] {Format.SINGLE_NO_PARENT});
    }

    /** Overrides the default format of {@link Format#MULTI}. */
    public FactoryBuilder injectFormat(Format format) {
      if (format == null) throw new NullPointerException("format == null");
      injectFormat = format;
      return this;
    }

    /** Overrides the injection format used for the indicated {@link Request#spanKind() span kind}. */
    public FactoryBuilder injectFormat(Span.Kind kind, Format format) {
      if (kind == null) throw new NullPointerException("kind == null");
      if (format == null) throw new NullPointerException("format == null");
      kindToInjectFormats.put(kind, new Format[] {format});
      return this;
    }

    /**
     * Like {@link #injectFormat}, but writes two formats.
     *
     * For example, you can set {@link Span.Kind#CLIENT} spans to inject both {@link Format#MULTI}
     * and {@link Format#SINGLE}, for transition use cases.
     */
    public FactoryBuilder injectFormats(Span.Kind kind, Format format1, Format format2) {
      if (kind == null) throw new NullPointerException("kind == null");
      if (format1 == null) throw new NullPointerException("format1 == null");
      if (format2 == null) throw new NullPointerException("format2 == null");
      if (format1.equals(format2)) throw new IllegalArgumentException("format1 == format2");
      if (!format1.equals(Format.MULTI) && !format2.equals(Format.MULTI)) {
        throw new IllegalArgumentException("One argument must be Format.MULTI");
      }
      kindToInjectFormats.put(kind, new Format[] {format1, format2});
      return this;
    }

    public Propagation.Factory build() {
      return new B3Propagation.Factory(this);
    }
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
  /**
   * "1" implies sampled and is a request to override collection-tier sampling policy.
   */
  static final String FLAGS_NAME = "X-B3-Flags";
  final K b3Key, traceIdKey, spanIdKey, parentSpanIdKey, sampledKey, debugKey;
  final List<K> fields;
  final Format[] injectFormats;
  final Map<Span.Kind, Format[]> kindToInjectFormats;

  B3Propagation(KeyFactory<K> keyFactory, Factory factory) {
    this.b3Key = keyFactory.create("b3");
    this.traceIdKey = keyFactory.create(TRACE_ID_NAME);
    this.spanIdKey = keyFactory.create(SPAN_ID_NAME);
    this.parentSpanIdKey = keyFactory.create(PARENT_SPAN_ID_NAME);
    this.sampledKey = keyFactory.create(SAMPLED_NAME);
    this.debugKey = keyFactory.create(FLAGS_NAME);
    this.injectFormats = new Format[] {factory.injectFormat};
    this.kindToInjectFormats = factory.kindToInjectFormats;

    // Scan for all formats in use
    boolean injectsMulti = factory.injectFormat.equals(Format.MULTI);
    boolean injectsSingle = !injectsMulti;
    for (Format[] formats : kindToInjectFormats.values()) {
      for (Format format : formats) {
        if (format.equals(Format.MULTI)) injectsMulti = true;
        if (!format.equals(Format.MULTI)) injectsSingle = true;
      }
    }

    // Convert the formats into a list of possible fields
    if (injectsMulti && injectsSingle) {
      this.fields = Collections.unmodifiableList(
        asList(b3Key, traceIdKey, spanIdKey, parentSpanIdKey, sampledKey, debugKey)
      );
    } else if (injectsMulti) {
      this.fields = Collections.unmodifiableList(
        asList(traceIdKey, spanIdKey, parentSpanIdKey, sampledKey, debugKey)
      );
    } else {
      this.fields = Collections.singletonList(b3Key);
    }
  }

  @Override public List<K> keys() {
    return fields;
  }

  @Override public <C> TraceContext.Injector<C> injector(Setter<C, K> setter) {
    if (setter == null) throw new NullPointerException("setter == null");
    return new B3Injector<>(this, setter);
  }

  static final class B3Injector<C, K> implements TraceContext.Injector<C> {
    final B3Propagation<K> propagation;
    final Setter<C, K> setter;

    B3Injector(B3Propagation<K> propagation, Setter<C, K> setter) {
      this.propagation = propagation;
      this.setter = setter;
    }

    @Override public void inject(TraceContext context, C carrier) {
      Format[] formats = propagation.injectFormats;
      if (carrier instanceof Request) {
        Span.Kind kind = ((Request) carrier).spanKind();
        formats = propagation.kindToInjectFormats.get(kind);
      }

      for (Format format : formats) {
        switch (format) {
          case SINGLE:
            setter.put(carrier, propagation.b3Key, writeB3SingleFormat(context));
            break;
          case SINGLE_NO_PARENT:
            setter.put(carrier, propagation.b3Key, writeB3SingleFormatWithoutParentId(context));
            break;
          case MULTI:
            injectMulti(context, carrier);
            break;
        }
      }
    }

    void injectMulti(TraceContext context, C carrier) {
      setter.put(carrier, propagation.traceIdKey, context.traceIdString());
      setter.put(carrier, propagation.spanIdKey, context.spanIdString());
      String parentId = context.parentIdString();
      if (parentId != null) setter.put(carrier, propagation.parentSpanIdKey, parentId);
      if (context.debug()) {
        setter.put(carrier, propagation.debugKey, "1");
      } else if (context.sampled() != null) {
        setter.put(carrier, propagation.sampledKey, context.sampled() ? "1" : "0");
      }
    }
  }

  @Override public <C> TraceContext.Extractor<C> extractor(Getter<C, K> getter) {
    if (getter == null) throw new NullPointerException("getter == null");
    return new B3Extractor<>(this, getter);
  }

  static final class B3Extractor<C, K> implements TraceContext.Extractor<C> {
    final B3Propagation<K> propagation;
    final Getter<C, K> getter;

    B3Extractor(B3Propagation<K> propagation, Getter<C, K> getter) {
      this.propagation = propagation;
      this.getter = getter;
    }

    @Override public TraceContextOrSamplingFlags extract(C carrier) {
      if (carrier == null) throw new NullPointerException("carrier == null");

      // try to extract single-header format
      String b3 = getter.get(carrier, propagation.b3Key);
      TraceContextOrSamplingFlags extracted = b3 != null ? parseB3SingleFormat(b3) : null;
      if (extracted != null) return extracted;

      // Start by looking at the sampled state as this is used regardless
      // Official sampled value is 1, though some old instrumentation send true
      String sampled = getter.get(carrier, propagation.sampledKey);
      Boolean sampledV = sampled != null
        ? sampled.equals("1") || sampled.equalsIgnoreCase("true")
        : null;
      boolean debug = "1".equals(getter.get(carrier, propagation.debugKey));

      String traceIdString = getter.get(carrier, propagation.traceIdKey);
      // It is ok to go without a trace ID, if sampling or debug is set
      if (traceIdString == null) return TraceContextOrSamplingFlags.create(sampledV, debug);

      // Try to parse the trace IDs into the context
      TraceContext.Builder result = TraceContext.newBuilder();
      if (result.parseTraceId(traceIdString, propagation.traceIdKey)
        && result.parseSpanId(getter, carrier, propagation.spanIdKey)
        && result.parseParentId(getter, carrier, propagation.parentSpanIdKey)) {
        if (sampledV != null) result.sampled(sampledV.booleanValue());
        if (debug) result.debug(true);
        return TraceContextOrSamplingFlags.create(result.build());
      }
      return TraceContextOrSamplingFlags.EMPTY; // trace context is malformed so return empty
    }
  }

  static final class Factory extends Propagation.Factory {
    final Format injectFormat;
    final Map<Span.Kind, Format[]> kindToInjectFormats;

    Factory(FactoryBuilder builder) {
      this.injectFormat = builder.injectFormat;
      this.kindToInjectFormats = new EnumMap<>(builder.kindToInjectFormats);
    }

    @Override public <K1> Propagation<K1> create(KeyFactory<K1> keyFactory) {
      return new B3Propagation<>(keyFactory, this);
    }

    @Override public boolean supportsJoin() {
      return true;
    }

    @Override public String toString() {
      return "B3PropagationFactory";
    }
  }
}

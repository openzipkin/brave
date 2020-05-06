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
import brave.Span;
import brave.internal.Platform;
import brave.internal.propagation.StringPropagationAdapter;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
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
// This type was never returned, but we keep the signature for historical reasons
public abstract class B3Propagation<K> implements Propagation<K> {
  /** Describes the formats used to inject headers. */
  public enum Format {
    /** The trace context is encoded with a several fields prefixed with "x-b3-". */
    MULTI,
    /** The trace context is encoded with {@link B3SingleFormat#writeB3SingleFormat(TraceContext)}. */
    SINGLE,
    /** The trace context is encoded with {@link B3SingleFormat#writeB3SingleFormatWithoutParentId(TraceContext)}. */
    SINGLE_NO_PARENT
  }

  public static final Propagation.Factory FACTORY = newFactoryBuilder().build();

  static final Propagation<String> INSTANCE = FACTORY.get();

  /**
   * Returns a singleton default instance.
   *
   * @since 5.12
   */
  public static Propagation<String> get() {
    return INSTANCE;
  }

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

  /** Header that encodes all trace ID properties in one value. */
  static final String B3 = "b3";
  /**
   * 128 or 64-bit trace ID lower-hex encoded into 32 or 16 characters (required)
   */
  static final String TRACE_ID = "X-B3-TraceId";
  /**
   * 64-bit span ID lower-hex encoded into 16 characters (required)
   */
  static final String SPAN_ID = "X-B3-SpanId";
  /**
   * 64-bit parent span ID lower-hex encoded into 16 characters (absent on root span)
   */
  static final String PARENT_SPAN_ID = "X-B3-ParentSpanId";
  /**
   * "1" means report this span to the tracing system, "0" means do not. (absent means defer the
   * decision to the receiver of this header).
   */
  static final String SAMPLED = "X-B3-Sampled";
  static final String SAMPLED_MALFORMED =
      "Invalid input: expected 0 or 1 for " + SAMPLED + ", but found '{0}'";

  /**
   * "1" implies sampled and is a request to override collection-tier sampling policy.
   */
  static final String FLAGS = "X-B3-Flags";

  static final class B3Injector<R> implements Injector<R> {
    final Factory factory;
    final Setter<R, String> setter;

    B3Injector(Factory factory, Setter<R, String> setter) {
      this.factory = factory;
      this.setter = setter;
    }

    @Override public void inject(TraceContext context, R request) {
      Format[] formats = factory.injectFormats;
      if (request instanceof Request) {
        Span.Kind kind = ((Request) request).spanKind();
        formats = factory.kindToInjectFormats.get(kind);
      }

      for (Format format : formats) {
        switch (format) {
          case SINGLE:
            setter.put(request, B3, writeB3SingleFormat(context));
            break;
          case SINGLE_NO_PARENT:
            setter.put(request, B3, writeB3SingleFormatWithoutParentId(context));
            break;
          case MULTI:
            injectMulti(context, request);
            break;
        }
      }
    }

    void injectMulti(TraceContext context, R request) {
      setter.put(request, TRACE_ID, context.traceIdString());
      setter.put(request, SPAN_ID, context.spanIdString());
      String parentId = context.parentIdString();
      if (parentId != null) setter.put(request, PARENT_SPAN_ID, parentId);
      if (context.debug()) {
        setter.put(request, FLAGS, "1");
      } else if (context.sampled() != null) {
        setter.put(request, SAMPLED, context.sampled() ? "1" : "0");
      }
    }
  }

  static final class B3Extractor<R> implements Extractor<R> {
    final Factory factory;
    final Getter<R, String> getter;

    B3Extractor(Factory factory, Getter<R, String> getter) {
      this.factory = factory;
      this.getter = getter;
    }

    @Override public TraceContextOrSamplingFlags extract(R request) {
      if (request == null) throw new NullPointerException("request == null");

      // try to extract single-header format
      String b3 = getter.get(request, B3);
      TraceContextOrSamplingFlags extracted = b3 != null ? parseB3SingleFormat(b3) : null;
      if (extracted != null) return extracted;

      // Start by looking at the sampled state as this is used regardless
      // Official sampled value is 1, though some old instrumentation send true
      String sampled = getter.get(request, SAMPLED);
      Boolean sampledV;
      if (sampled == null) {
        sampledV = null; // defer decision
      } else if (sampled.length() == 1) { // handle fast valid paths
        char sampledC = sampled.charAt(0);

        if (sampledC == '1') {
          sampledV = true;
        } else if (sampledC == '0') {
          sampledV = false;
        } else {
          Platform.get().log(SAMPLED_MALFORMED, sampled, null);
          return TraceContextOrSamplingFlags.EMPTY; // trace context is malformed so return empty
        }
      } else if (sampled.equalsIgnoreCase("true")) { // old clients
        sampledV = true;
      } else if (sampled.equalsIgnoreCase("false")) { // old clients
        sampledV = false;
      } else {
        Platform.get().log(SAMPLED_MALFORMED, sampled, null);
        return TraceContextOrSamplingFlags.EMPTY; // Restart trace instead of propagating false
      }

      // The only flag we action is 1, but it could be that any integer is present.
      // Here, we leniently parse as debug is not a primary consideration of the trace context.
      boolean debug = "1".equals(getter.get(request, FLAGS));

      String traceIdString = getter.get(request, TRACE_ID);

      // It is ok to go without a trace ID, if sampling or debug is set
      if (traceIdString == null) {
        if (debug) return TraceContextOrSamplingFlags.DEBUG;
        if (sampledV != null) {
          return sampledV
              ? TraceContextOrSamplingFlags.SAMPLED
              : TraceContextOrSamplingFlags.NOT_SAMPLED;
        }
      }

      // Try to parse the trace IDs into the context
      TraceContext.Builder result = TraceContext.newBuilder();
      if (result.parseTraceId(traceIdString, TRACE_ID)
          && result.parseSpanId(getter, request, SPAN_ID)
          && result.parseParentId(getter, request, PARENT_SPAN_ID)) {
        if (sampledV != null) result.sampled(sampledV.booleanValue());
        if (debug) result.debug(true);
        return TraceContextOrSamplingFlags.create(result.build());
      }
      return TraceContextOrSamplingFlags.EMPTY; // trace context is malformed so return empty
    }
  }

  static final class Factory extends Propagation.Factory implements Propagation<String> {
    final List<String> keyNames;
    final Format[] injectFormats;
    final Map<Span.Kind, Format[]> kindToInjectFormats;

    Factory(FactoryBuilder builder) {
      this.injectFormats = new Format[] {builder.injectFormat};
      this.kindToInjectFormats = new EnumMap<>(builder.kindToInjectFormats);

      // Scan for all formats in use
      boolean injectsMulti = builder.injectFormat.equals(Format.MULTI);
      boolean injectsSingle = !injectsMulti;
      for (Format[] formats : kindToInjectFormats.values()) {
        for (Format format : formats) {
          if (format.equals(Format.MULTI)) injectsMulti = true;
          if (!format.equals(Format.MULTI)) injectsSingle = true;
        }
      }

      // Convert the formats into a list of possible fields
      if (injectsMulti && injectsSingle) {
        this.keyNames = Collections.unmodifiableList(
            asList(B3, TRACE_ID, SPAN_ID, PARENT_SPAN_ID, SAMPLED, FLAGS)
        );
      } else if (injectsMulti) {
        this.keyNames = Collections.unmodifiableList(
            asList(TRACE_ID, SPAN_ID, PARENT_SPAN_ID, SAMPLED, FLAGS)
        );
      } else {
        this.keyNames = Collections.singletonList(B3);
      }
    }

    @Override public List<String> keys() {
      return keyNames;
    }

    @Override public Propagation<String> get() {
      return this;
    }

    @Override public <K1> Propagation<K1> create(KeyFactory<K1> keyFactory) {
      return StringPropagationAdapter.create(this, keyFactory);
    }

    @Override public boolean supportsJoin() {
      return true;
    }

    @Override public <R> Injector<R> injector(Setter<R, String> setter) {
      if (setter == null) throw new NullPointerException("setter == null");
      return new B3Injector<>(this, setter);
    }

    @Override public <R> Extractor<R> extractor(Getter<R, String> getter) {
      if (getter == null) throw new NullPointerException("getter == null");
      return new B3Extractor<>(this, getter);
    }

    @Override public String toString() {
      return "B3Propagation";
    }
  }

  B3Propagation() { // no instances
  }
}

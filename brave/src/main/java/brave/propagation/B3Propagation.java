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
import brave.Span.Kind;
import brave.internal.Platform;
import brave.internal.propagation.InjectorFactory;
import brave.internal.propagation.InjectorFactory.InjectorFunction;
import brave.internal.propagation.StringPropagationAdapter;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import java.util.Collections;
import java.util.List;

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
  public enum Format implements InjectorFunction {
    /** The trace context is encoded with a several fields prefixed with "x-b3-". */
    MULTI() {
      @Override public List<String> keyNames() {
        return MULTI_KEY_NAMES;
      }

      @Override public <R> void inject(Setter<R, String> setter, TraceContext context, R request) {
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
    },
    /** The trace context is encoded with {@link B3SingleFormat#writeB3SingleFormat(TraceContext)}. */
    SINGLE() {
      @Override public List<String> keyNames() {
        return SINGLE_KEY_NAMES;
      }

      @Override public <R> void inject(Setter<R, String> setter, TraceContext context, R request) {
        setter.put(request, B3, writeB3SingleFormat(context));
      }
    },
    /** The trace context is encoded with {@link B3SingleFormat#writeB3SingleFormatWithoutParentId(TraceContext)}. */
    SINGLE_NO_PARENT() {
      @Override public List<String> keyNames() {
        return SINGLE_KEY_NAMES;
      }

      @Override public <R> void inject(Setter<R, String> setter, TraceContext context, R request) {
        setter.put(request, B3, writeB3SingleFormatWithoutParentId(context));
      }
    };

    static final List<String> SINGLE_KEY_NAMES = Collections.singletonList(B3);
    static final List<String> MULTI_KEY_NAMES = Collections.unmodifiableList(
        asList(TRACE_ID, SPAN_ID, PARENT_SPAN_ID, SAMPLED, FLAGS)
    );
  }

  public static final Propagation.Factory FACTORY = new B3Propagation.Factory(newFactoryBuilder());

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
    InjectorFactory.Builder injectorFactoryBuilder = InjectorFactory.newBuilder(Format.MULTI)
        .clientInjectorFunctions(Format.MULTI)
        .producerInjectorFunctions(Format.SINGLE_NO_PARENT)
        .consumerInjectorFunctions(Format.SINGLE_NO_PARENT);

    /**
     * Overrides the injection format for non-remote requests, such as message processors. Defaults
     * to {@link Format#MULTI}.
     */
    public FactoryBuilder injectFormat(Format format) {
      if (format == null) throw new NullPointerException("format == null");
      injectorFactoryBuilder.injectorFunctions(format);
      return this;
    }

    /**
     * Overrides the injection format used for the indicated {@link Request#spanKind() span kind}.
     *
     * <p><em>Note</em>: {@link Kind#SERVER} is not a valid inject format, and will be ignored.
     */
    public FactoryBuilder injectFormat(Kind kind, Format format) {
      if (kind == null) throw new NullPointerException("kind == null");
      if (format == null) throw new NullPointerException("format == null");
      switch (kind) { // handle injectable formats
        case CLIENT:
          injectorFactoryBuilder.clientInjectorFunctions(format);
          break;
        case PRODUCER:
          injectorFactoryBuilder.producerInjectorFunctions(format);
          break;
        case CONSUMER:
          injectorFactoryBuilder.consumerInjectorFunctions(format);
          break;
        default: // SERVER is nonsense as it cannot be injected
      }
      return this;
    }

    /**
     * Like {@link #injectFormat}, but writes two formats.
     *
     * For example, you can set {@link Kind#CLIENT} spans to inject both {@link Format#MULTI} and
     * {@link Format#SINGLE}, for transition use cases.
     */
    public FactoryBuilder injectFormats(Kind kind, Format format1, Format format2) {
      if (kind == null) throw new NullPointerException("kind == null");
      if (format1 == null) throw new NullPointerException("format1 == null");
      if (format2 == null) throw new NullPointerException("format2 == null");
      if (format1.equals(format2)) throw new IllegalArgumentException("format1 == format2");
      if (!format1.equals(Format.MULTI) && !format2.equals(Format.MULTI)) {
        throw new IllegalArgumentException("One argument must be Format.MULTI");
      }
      switch (kind) { // handle injectable formats
        case CLIENT:
          injectorFactoryBuilder.clientInjectorFunctions(format1, format2);
          break;
        case PRODUCER:
          injectorFactoryBuilder.producerInjectorFunctions(format1, format2);
          break;
        case CONSUMER:
          injectorFactoryBuilder.consumerInjectorFunctions(format1, format2);
          break;
        default: // SERVER is nonsense as it cannot be injected
      }
      return this;
    }

    public Propagation.Factory build() {
      Factory result = new Factory(this);
      if (result.equals(FACTORY)) return FACTORY;
      return result;
    }

    FactoryBuilder() {
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

  static final class Factory extends Propagation.Factory implements Propagation<String> {
    final InjectorFactory injectorFactory;

    Factory(FactoryBuilder builder) {
      injectorFactory = builder.injectorFactoryBuilder.build();
    }

    @Override public List<String> keys() {
      return injectorFactory.keyNames();
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
      return injectorFactory.newInjector(setter);
    }

    @Override public <R> Extractor<R> extractor(Getter<R, String> getter) {
      if (getter == null) throw new NullPointerException("getter == null");
      return new B3Extractor<>(this, getter);
    }

    @Override public int hashCode() {
      return injectorFactory.hashCode();
    }

    @Override public boolean equals(Object o) {
      if (o == this) return true;
      if (!(o instanceof B3Propagation.Factory)) return false;

      B3Propagation.Factory that = (B3Propagation.Factory) o;
      return injectorFactory.equals(that.injectorFactory);
    }

    @Override public String toString() {
      return "B3Propagation";
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

  B3Propagation() { // no instances
  }
}

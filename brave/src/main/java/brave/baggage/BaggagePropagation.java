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
package brave.baggage;

import brave.baggage.BaggagePropagationConfig.SingleBaggageField;
import brave.internal.Nullable;
import brave.internal.baggage.BaggageCodec;
import brave.internal.baggage.BaggageFields;
import brave.internal.collect.Lists;
import brave.internal.propagation.StringPropagationAdapter;
import brave.propagation.ExtraFieldPropagation;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static brave.internal.baggage.ExtraBaggageContext.findExtra;

/**
 * This implements in-process and remote {@linkplain BaggageField baggage} propagation.
 *
 * <p>For example, if you have a need to know the a specific request's country code, you can
 * propagate it through the trace as HTTP headers.
 * <pre>{@code
 * import brave.baggage.BaggagePropagationConfig.SingleBaggageField;
 *
 * // Configure your baggage field
 * COUNTRY_CODE = BaggageField.create("country-code");
 *
 * // When you initialize the builder, add the baggage you want to propagate
 * tracingBuilder.propagationFactory(
 *   BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY)
 *                     .add(SingleBaggageField.remote(COUNTRY_CODE))
 *                     .build()
 * );
 *
 * // later, you can tag that country code
 * Tags.BAGGAGE_FIELD.tag(COUNTRY_CODE, span);
 * }</pre>
 *
 * <p>See {@link BaggageField} for baggage usage examples.
 *
 * <h3>Customizing propagation keys</h3>
 * {@link SingleBaggageField#remote(BaggageField)} sets the name used as a propagation key (header)
 * to the lowercase variant of the field name. You can override this by supplying different key
 * names. Note: they will be lower-cased.
 *
 * <p>For example, the following will propagate the field "x-vcap-request-id" as-is, but send the
 * fields "countryCode" and "userId" on the wire as "baggage-country-code" and "baggage-user-id"
 * respectively.
 *
 * <pre>{@code
 * import brave.baggage.BaggagePropagationConfig.SingleBaggageField;
 *
 * REQUEST_ID = BaggageField.create("x-vcap-request-id");
 * COUNTRY_CODE = BaggageField.create("countryCode");
 * USER_ID = BaggageField.create("userId");
 *
 * tracingBuilder.propagationFactory(
 *     BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY)
 *                       .add(SingleBaggageField.remote(REQUEST_ID))
 *                       .add(SingleBaggageField.newBuilder(COUNTRY_CODE)
 *                                              .addKeyName("baggage-country-code").build())
 *                       .add(SingleBaggageField.newBuilder(USER_ID)
 *                                              .addKeyName("baggage-user-id").build())
 *                       .build()
 * );
 * }</pre>
 *
 * @see BaggageField
 * @see BaggagePropagationConfig
 * @see BaggagePropagationCustomizer
 * @see CorrelationScopeDecorator
 * @since 5.11
 */
public final class BaggagePropagation<K> implements Propagation<K> {
  /** Wraps an underlying propagation implementation, pushing one or more fields. */
  public static FactoryBuilder newFactoryBuilder(Propagation.Factory delegate) {
    return new FactoryBuilder(delegate);
  }

  public static class FactoryBuilder { // not final to backport ExtraFieldPropagation
    final Propagation.Factory delegate;
    final List<String> extractKeyNames = new ArrayList<>();
    final Set<BaggagePropagationConfig> configs = new LinkedHashSet<>();

    FactoryBuilder(Propagation.Factory delegate) {
      if (delegate == null) throw new NullPointerException("delegate == null");
      this.delegate = delegate;
    }

    /**
     * Returns an immutable copy of the current {@linkplain #add(BaggagePropagationConfig)
     * configuration}. This allows those who can't create the builder to reconfigure this builder.
     *
     * @see #clear()
     * @since 5.11
     */
    public Set<BaggagePropagationConfig> configs() {
      return Collections.unmodifiableSet(new LinkedHashSet<>(configs));
    }

    /**
     * Clears all state. This allows those who can't create the builder to reconfigure fields.
     *
     * @see #configs()
     * @see BaggagePropagationCustomizer
     * @since 5.11
     */
    public FactoryBuilder clear() {
      extractKeyNames.clear();
      configs.clear();
      return this;
    }

    /** @since 5.11 */
    public FactoryBuilder add(BaggagePropagationConfig config) {
      if (config == null) throw new NullPointerException("config == null");
      if (configs.contains(config)) {
        throw new IllegalArgumentException(config + " already added");
      }
      for (String extractKeyName : config.baggageCodec.extractKeyNames()) {
        if (extractKeyNames.contains(extractKeyName)) {
          throw new IllegalArgumentException("Propagation key already in use: " + extractKeyName);
        }
        extractKeyNames.add(extractKeyName);
      }

      configs.add(config);
      return this;
    }

    /** Returns the delegate if there are no fields to propagate. */
    public Propagation.Factory build() {
      if (configs.isEmpty()) return delegate;
      return new Factory(this);
    }
  }

  /** Stored in {@link TraceContextOrSamplingFlags#extra()} or {@link TraceContext#extra()} */
  static final class Extra {
    final List<String> extractKeyNames;

    Extra(List<String> extractKeyNames) {
      this.extractKeyNames = extractKeyNames;
    }
  }

  static final class Factory extends Propagation.Factory implements Propagation<String> {
    final Propagation.Factory delegateFactory;
    final Propagation<String> delegate;
    final BaggageFields.Factory baggageFactory;
    final BaggagePropagationConfig[] configs;
    final String[] localFieldNames;
    @Nullable final Extra extra;

    Factory(FactoryBuilder factoryBuilder) {
      this.delegateFactory = factoryBuilder.delegate;
      this.delegate = delegateFactory.get();

      // Don't add another "extra" if there are only local fields
      List<String> extractKeyNames = Lists.ensureImmutable(factoryBuilder.extractKeyNames);
      this.extra = !extractKeyNames.isEmpty() ? new Extra(extractKeyNames) : null;

      // Associate baggage fields with any remote propagation keys
      this.configs = factoryBuilder.configs.toArray(new BaggagePropagationConfig[0]);

      List<BaggageField> fields = new ArrayList<>();
      Set<String> localFieldNames = new LinkedHashSet<>();
      int maxDynamicFields = 0;
      for (BaggagePropagationConfig config : factoryBuilder.configs) {
        maxDynamicFields += config.maxDynamicFields;
        if (config instanceof SingleBaggageField) {
          BaggageField field = ((SingleBaggageField) config).field;
          fields.add(field);
          if (config.baggageCodec == BaggageCodec.NOOP) localFieldNames.add(field.name());
        }
      }
      this.baggageFactory = BaggageFields.newFactory(fields, maxDynamicFields);
      this.localFieldNames = localFieldNames.toArray(new String[0]);
    }

    @Deprecated @Override public <K1> BaggagePropagation<K1> create(KeyFactory<K1> keyFactory) {
      return new BaggagePropagation<>(StringPropagationAdapter.create(get(), keyFactory));
    }

    @Override public BaggagePropagation<String> get() {
      return new BaggagePropagation<>(this);
    }

    @Override public TraceContext decorate(TraceContext context) {
      TraceContext result = delegateFactory.decorate(context);
      return baggageFactory.decorate(result);
    }

    @Override public boolean supportsJoin() {
      return delegateFactory.supportsJoin();
    }

    @Override public boolean requires128BitTraceId() {
      return delegateFactory.requires128BitTraceId();
    }

    @Override public List<String> keys() {
      return delegate.keys();
    }

    @Override public <R> Injector<R> injector(Setter<R, String> setter) {
      return new BaggageInjector<>(this, setter);
    }

    @Override public <R> Extractor<R> extractor(Getter<R, String> getter) {
      return new BaggageExtractor<>(this, getter);
    }
  }

  final Propagation<K> delegate;

  BaggagePropagation(Propagation<K> delegate) {
    this.delegate = delegate;
  }

  /**
   * Only returns trace context keys. Baggage field names are not returned to ensure tools don't
   * delete them. This is to support users accessing baggage without Brave apis (ex via headers).
   */
  @Override public List<K> keys() {
    return delegate.keys();
  }

  /**
   * Returns the key names used for propagation, including those used for the {@linkplain #keys()
   * trace context} and {@linkplain SingleBaggageField#keyNames() baggage}. The result can be cached
   * in the same scope as the propagation instance.
   *
   * <p>This is here for the remote propagation use cases:
   * <ul>
   *   <li>To generate constants for all key names. ex. gRPC Metadata.Key</li>
   *   <li>To iterate fields when missing a get field by name function. ex. OpenTracing TextMap</li>
   *   <li>To clear fields on re-usable requests. ex. JMS message</li>
   * </ul>
   *
   * <h3>Details</h3>
   * The {@code propagation} parameter is required because there may be multiple tracers with
   * different baggage configuration. Also, {@link Propagation} instances can be wrapped, so you
   * cannot use {@code instanceof} to identify if baggage is internally supported. For example,
   * {@link ExtraFieldPropagation} internally wraps {@link BaggagePropagation}.
   *
   * <p>This is different than {@link BaggageField#getAll(TraceContext)}, as propagation keys may be
   * different than {@linkplain BaggageField#name() baggage field names}.
   *
   * @param propagation used to extract configuration
   * @return a list of remote propagation key names used for trace context and baggage.
   * @since 5.12
   */
  // On OpenTracing TextMap: https://github.com/opentracing/opentracing-java/issues/305
  public static List<String> allKeyNames(Propagation<String> propagation) {
    if (propagation == null) throw new NullPointerException("propagation == null");
    // When baggage or similar is in use, the result != TraceContextOrSamplingFlags.EMPTY
    TraceContextOrSamplingFlags emptyExtraction =
      propagation.extractor(NoopGetter.INSTANCE).extract(Boolean.TRUE);
    List<String> baggageKeyNames = getAllKeyNames(emptyExtraction);
    if (baggageKeyNames.isEmpty()) return propagation.keys();

    List<String> result = new ArrayList<>(propagation.keys().size() + baggageKeyNames.size());
    result.addAll(propagation.keys());
    result.addAll(baggageKeyNames);
    return Collections.unmodifiableList(result);
  }

  // Not lambda as Retrolambda creates an OSGi dependency on jdk.internal.vm.annotation with JDK 14
  // See https://github.com/luontola/retrolambda/issues/160
  enum NoopGetter implements Getter<Boolean, String> {
    INSTANCE;

    @Override public String get(Boolean request, String key) {
      return null;
    }
  }

  static List<String> getAllKeyNames(TraceContextOrSamplingFlags extracted) {
    List<Object> extraList =
        extracted.context() != null ? extracted.context().extra() : extracted.extra();
    Extra extra = findExtra(Extra.class, extraList);
    if (extra == null) return Collections.emptyList();
    return extra.extractKeyNames;
  }

  @Override public <R> Injector<R> injector(Setter<R, K> setter) {
    return delegate.injector(setter);
  }

  @Override public <R> Extractor<R> extractor(Getter<R, K> getter) {
    return delegate.extractor(getter);
  }

  static final class BaggageInjector<R> implements Injector<R> {
    final Injector<R> delegate;
    final Factory factory;
    final Setter<R, String> setter;

    BaggageInjector(Factory factory, Setter<R, String> setter) {
      this.delegate = factory.delegate.injector(setter);
      this.factory = factory;
      this.setter = setter;
    }

    @Override public void inject(TraceContext context, R request) {
      delegate.inject(context, request);
      BaggageFields extra = context.findExtra(BaggageFields.class);
      if (extra == null) return;
      Map<String, String> values =
          extra.toMapFilteringFieldNames(factory.localFieldNames);
      if (values.isEmpty()) return;

      for (BaggagePropagationConfig config : factory.configs) {
        if (config.baggageCodec == BaggageCodec.NOOP) continue; // local field

        String value = config.baggageCodec.encode(values, context, request);
        if (value == null) continue;

        List<String> keys = config.baggageCodec.injectKeyNames();
        for (int i = 0, length = keys.size(); i < length; i++) {
          setter.put(request, keys.get(i), value);
        }
      }
    }
  }

  static final class BaggageExtractor<R> implements Extractor<R> {
    final Factory factory;
    final Extractor<R> delegate;
    final Getter<R, String> getter;

    BaggageExtractor(Factory factory, Getter<R, String> getter) {
      this.delegate = factory.delegate.extractor(getter);
      this.factory = factory;
      this.getter = getter;
    }

    @Override public TraceContextOrSamplingFlags extract(R request) {
      TraceContextOrSamplingFlags.Builder builder = delegate.extract(request).toBuilder();
      BaggageFields extra = factory.baggageFactory.create();
      builder.addExtra(extra);

      if (factory.extra == null) return builder.build();

      for (BaggagePropagationConfig config : factory.configs) {
        if (config.baggageCodec == BaggageCodec.NOOP) continue; // local field

        List<String> keys = config.baggageCodec.injectKeyNames();
        for (int i = 0, length = keys.size(); i < length; i++) {
          String value = getter.get(request, keys.get(i));
          if (value != null && config.baggageCodec.decode(extra, request, value)) {
            break; // accept the first match
          }
        }
      }

      return builder.addExtra(factory.extra).build();
    }
  }
}

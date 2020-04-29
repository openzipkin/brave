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
import brave.internal.Lists;
import brave.internal.Nullable;
import brave.internal.baggage.BaggageHandler;
import brave.internal.baggage.BaggageHandler.StateDecoder;
import brave.internal.baggage.BaggageHandler.StateEncoder;
import brave.internal.baggage.ExtraBaggageFields;
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
public class BaggagePropagation<K> implements Propagation<K> {
  /** Wraps an underlying propagation implementation, pushing one or more fields. */
  public static FactoryBuilder newFactoryBuilder(Propagation.Factory delegate) {
    return new FactoryBuilder(delegate);
  }

  public static class FactoryBuilder { // not final to backport ExtraFieldPropagation
    final Propagation.Factory delegate;
    final List<String> extractKeyNames = new ArrayList<>();
    final Set<BaggagePropagationConfig<?>> configs = new LinkedHashSet<>();

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
    // we can't make this Set<BaggagePropagationConfig<?>> as it will break source compat with 5.11
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
    public FactoryBuilder add(BaggagePropagationConfig<?> config) {
      if (config == null) throw new NullPointerException("config == null");
      if (configs.contains(config)) {
        throw new IllegalArgumentException(config + " already added");
      }
      for (String extractKeyName : config.extractKeyNames()) {
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

  /** Only instantiated for remote baggage handling. */
  static final class RemoteHandler<S, K> {
    final BaggageHandler<S> baggageHandler;
    final K[] extractKeys;
    final StateDecoder<S> stateDecoder;
    final K[] injectKeys;
    final StateEncoder<S> stateEncoder;

    RemoteHandler(BaggagePropagationConfig<S> config, K[] extractKeys, K[] injectKeys) {
      this.baggageHandler = config.baggageHandler;
      this.extractKeys = extractKeys;
      this.stateDecoder = config.stateDecoder;
      this.injectKeys = injectKeys;
      this.stateEncoder = config.stateEncoder;
    }

    @Nullable <R> String encodeState(ExtraBaggageFields extra, TraceContext context, R request) {
      S state = extra.getState(baggageHandler);
      if (state == null) return null;
      return stateEncoder.encode(state, context, request);
    }

    <R> boolean decodeState(ExtraBaggageFields extra, R request, String value) {
      S state = stateDecoder.decode(request, value);
      if (state == null) return false;
      extra.putState(baggageHandler, state);
      return true;
    }
  }

  /** Stored in {@link TraceContextOrSamplingFlags#extra()} or {@link TraceContext#extra()} */
  static final class ExtractKeyNames {
    final List<String> list;

    ExtractKeyNames(List<String> list) {
      this.list = list;
    }
  }

  static final class Factory extends Propagation.Factory {
    final Propagation.Factory delegate;
    final ExtraBaggageFields.Factory extraFactory;
    final BaggagePropagationConfig<?>[] configs;
    @Nullable final ExtractKeyNames extractKeyNames;

    Factory(FactoryBuilder factoryBuilder) {
      this.delegate = factoryBuilder.delegate;

      // Don't add another "extra" if there are only local fields
      List<String> extractKeyNames = Lists.ensureImmutable(factoryBuilder.extractKeyNames);
      this.extractKeyNames =
          !extractKeyNames.isEmpty() ? new ExtractKeyNames(extractKeyNames) : null;

      // Associate baggage handlers with any remote propagation keys
      this.configs = factoryBuilder.configs.toArray(new BaggagePropagationConfig<?>[0]);

      List<BaggageHandler<?>> handlers = new ArrayList<>();
      for (BaggagePropagationConfig<?> config : factoryBuilder.configs) {
        handlers.add(config.baggageHandler);
      }
      this.extraFactory = ExtraBaggageFields.newFactory(handlers);
    }

    @Override public BaggagePropagation<String> get() {
      return create(KeyFactory.STRING);
    }

    @Override public <K> BaggagePropagation<K> create(KeyFactory<K> keyFactory) {
      List<RemoteHandler<?, K>> configWithKeys = new ArrayList<>();
      for (BaggagePropagationConfig<?> config : configs) {
        if (config.extractKeyNames().isEmpty() && config.injectKeyNames().isEmpty()) continue;

        K[] extractKeys = newKeyArray(keyFactory, config.extractKeyNames());
        K[] injectKeys = newKeyArray(keyFactory, config.injectKeyNames());
        configWithKeys.add(new RemoteHandler<>(config, extractKeys, injectKeys));
      }
      return new BaggagePropagation<>(delegate.create(keyFactory), this, configWithKeys);
    }

    @Override public TraceContext decorate(TraceContext context) {
      TraceContext result = delegate.decorate(context);
      return extraFactory.decorate(result);
    }

    @Override public boolean supportsJoin() {
      return delegate.supportsJoin();
    }

    @Override public boolean requires128BitTraceId() {
      return delegate.requires128BitTraceId();
    }
  }

  final Propagation<K> delegate;
  final Factory factory;
  final RemoteHandler<?, K>[] remoteHandlers;

  BaggagePropagation(Propagation<K> delegate, Factory factory,
      List<RemoteHandler<?, K>> remoteHandlers) {
    this.delegate = delegate;
    this.factory = factory;
    this.remoteHandlers = remoteHandlers.toArray(new RemoteHandler[0]);
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
        propagation.extractor((c, k) -> null).extract(Boolean.TRUE);
    List<String> baggageKeyNames = getAllKeyNames(emptyExtraction);
    if (baggageKeyNames.isEmpty()) return propagation.keys();

    List<String> result = new ArrayList<>(propagation.keys().size() + baggageKeyNames.size());
    result.addAll(propagation.keys());
    result.addAll(baggageKeyNames);
    return Collections.unmodifiableList(result);
  }

  static List<String> getAllKeyNames(TraceContextOrSamplingFlags extracted) {
    List<Object> extra =
        extracted.context() != null ? extracted.context().extra() : extracted.extra();
    ExtractKeyNames extractKeyNames = findExtra(ExtractKeyNames.class, extra);
    if (extractKeyNames == null) return Collections.emptyList();
    return extractKeyNames.list;
  }

  @Override public <R> Injector<R> injector(Setter<R, K> setter) {
    return new BaggageInjector<>(this, setter);
  }

  @Override public <R> Extractor<R> extractor(Getter<R, K> getter) {
    return new BaggageExtractor<>(this, getter);
  }

  static final class BaggageInjector<R, K> implements Injector<R> {
    final BaggagePropagation<K> propagation;
    final Injector<R> delegate;
    final Setter<R, K> setter;

    BaggageInjector(BaggagePropagation<K> propagation, Setter<R, K> setter) {
      this.propagation = propagation;
      this.delegate = propagation.delegate.injector(setter);
      this.setter = setter;
    }

    @Override public void inject(TraceContext context, R request) {
      delegate.inject(context, request);
      ExtraBaggageFields extra = context.findExtra(ExtraBaggageFields.class);
      if (extra == null) return;
      inject(extra, context, request);
    }

    void inject(ExtraBaggageFields extra, TraceContext context, R request) {
      for (RemoteHandler<?, K> remoteHandler : propagation.remoteHandlers) {
        String value = remoteHandler.encodeState(extra, context, request);
        if (value == null) continue;
        for (K key : remoteHandler.extractKeys) setter.put(request, key, value);
      }
    }
  }

  static final class BaggageExtractor<R, K> implements Extractor<R> {
    final BaggagePropagation<K> propagation;
    final Extractor<R> delegate;
    final Getter<R, K> getter;

    BaggageExtractor(BaggagePropagation<K> propagation, Getter<R, K> getter) {
      this.propagation = propagation;
      this.delegate = propagation.delegate.extractor(getter);
      this.getter = getter;
    }

    @Override public TraceContextOrSamplingFlags extract(R request) {
      TraceContextOrSamplingFlags result = delegate.extract(request);

      // Always allocate as fields could be local-only or have values added late
      ExtraBaggageFields extra = propagation.factory.extraFactory.create();
      TraceContextOrSamplingFlags.Builder builder = result.toBuilder().addExtra(extra);

      if (propagation.factory.extractKeyNames == null) return builder.build();

      for (RemoteHandler<?, K> remoteHandler : propagation.remoteHandlers) {
        for (K key : remoteHandler.extractKeys) { // possibly multiple keys when prefixes are in use
          String value = getter.get(request, key);
          if (value != null && remoteHandler.decodeState(extra, request, value)) {
            break; // accept the first match
          }
        }
      }

      return builder.addExtra(propagation.factory.extractKeyNames).build();
    }
  }

  static <K1> K1[] newKeyArray(KeyFactory<K1> keyFactory, List<String> keyNames) {
    K1[] keys = (K1[]) new Object[keyNames.size()];
    for (int k = 0; k < keys.length; k++) {
      keys[k] = keyFactory.create(keyNames.get(k));
    }
    return keys;
  }
}

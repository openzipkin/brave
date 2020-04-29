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
import brave.internal.baggage.BaggageHandlers;
import brave.internal.baggage.ExtraBaggageFields;
import brave.internal.baggage.RemoteBaggageHandler;
import brave.propagation.ExtraFieldPropagation;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
    final List<String> allKeyNames = new ArrayList<>();
    final Map<SingleBaggageField, BaggageHandler<?>> configToHandler = new LinkedHashMap<>();
    final Set<SingleBaggageField> configs = new LinkedHashSet<>();

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
      allKeyNames.clear();
      configToHandler.clear();
      configs.clear();
      return this;
    }

    /** @since 5.11 */
    public FactoryBuilder add(BaggagePropagationConfig config) {
      if (config == null) throw new NullPointerException("config == null");
      if (!(config instanceof SingleBaggageField)) {
        throw new UnsupportedOperationException("dynamic fields not yet supported");
      }
      SingleBaggageField fieldConfig = (SingleBaggageField) config;
      BaggageHandler<String> handler = fieldConfig.keyNames().isEmpty()
          ? BaggageHandlers.string(fieldConfig.field())
          : BaggageHandlers.remoteString(fieldConfig);
      return add(fieldConfig, handler);
    }

    FactoryBuilder add(SingleBaggageField config, BaggageHandler<?> handler) { // internal
      if (configToHandler.containsKey(config)) {
        throw new IllegalArgumentException(config.field.name + " already added");
      }
      for (String keyName : config.keyNames) {
        if (allKeyNames.contains(keyName)) {
          throw new IllegalArgumentException("Propagation key already in use: " + keyName);
        }
        allKeyNames.add(keyName);
      }

      configs.add(config);
      configToHandler.put(config, handler);
      return this;
    }

    /** Returns the delegate if there are no fields to propagate. */
    public Propagation.Factory build() {
      if (configToHandler.isEmpty()) return delegate;
      return new Factory(this);
    }
  }

  /** For {@link Propagation.Factory#create(KeyFactory)} */
  static final class RemoteHandlerWithKeys<K> {
    final RemoteBaggageHandler<?> handler;
    final K[] keys;

    RemoteHandlerWithKeys(
        RemoteBaggageHandler<?> handler, String[] keyNames, KeyFactory<K> keyFactory) {
      this.handler = handler;
      ArrayList<K> keysList = new ArrayList<>();
      for (String keyName : keyNames) {
        keysList.add(keyFactory.create(keyName));
      }
      this.keys = (K[]) keysList.toArray();
    }
  }

  /** Stored in {@link TraceContextOrSamplingFlags#extra()} or {@link TraceContext#extra()} */
  static final class AllKeyNames {
    final List<String> list;

    AllKeyNames(List<String> list) {
      this.list = list;
    }
  }

  static final class Factory extends Propagation.Factory {
    final Propagation.Factory delegate;
    @Nullable final AllKeyNames allKeyNames;
    final String[][] keyNames;
    final BaggageHandler<?>[] handlers;

    final ExtraBaggageFields.Factory extraFactory;

    Factory(FactoryBuilder factoryBuilder) {
      this.delegate = factoryBuilder.delegate;

      // Don't add another "extra" if there are only local fields
      List<String> allKeyNames = Lists.ensureImmutable(factoryBuilder.allKeyNames);
      this.allKeyNames = !allKeyNames.isEmpty() ? new AllKeyNames(allKeyNames) : null;

      // Associate baggage handlers with any remote propagation keys
      int i = 0, length = factoryBuilder.configToHandler.size();
      this.keyNames = new String[length][];
      this.handlers = new BaggageHandler<?>[length];
      for (Entry<SingleBaggageField, BaggageHandler<?>> entry : factoryBuilder.configToHandler.entrySet()) {
        this.keyNames[i] = entry.getKey().keyNames().toArray(new String[0]);
        this.handlers[i] = entry.getValue();
        i++;
      }
      this.extraFactory = ExtraBaggageFields.newFactory(handlers);
    }

    @Override public BaggagePropagation<String> get() {
      return create(KeyFactory.STRING);
    }

    @Override public <K> BaggagePropagation<K> create(KeyFactory<K> keyFactory) {
      List<RemoteHandlerWithKeys<K>> remoteHandlersWithKeys = new ArrayList<>();
      for (int i = 0; i < handlers.length; i++) {
        if (!(handlers[i] instanceof RemoteBaggageHandler)) continue;
        RemoteBaggageHandler<?> handler = (RemoteBaggageHandler<?>) handlers[i];
        remoteHandlersWithKeys.add(new RemoteHandlerWithKeys<>(handler, keyNames[i], keyFactory));
      }
      return new BaggagePropagation<>(this, keyFactory, remoteHandlersWithKeys);
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
  final RemoteHandlerWithKeys<K>[] remoteHandlersWithKeys;

  BaggagePropagation(Factory factory, KeyFactory<K> keyFactory,
      List<RemoteHandlerWithKeys<K>> remoteHandlersWithKeys) {
    this.delegate = factory.delegate.create(keyFactory);
    this.factory = factory;
    this.remoteHandlersWithKeys = remoteHandlersWithKeys.toArray(new RemoteHandlerWithKeys[0]);
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
    AllKeyNames allKeyNames = findExtra(AllKeyNames.class, extra);
    if (allKeyNames == null) return Collections.emptyList();
    return allKeyNames.list;
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

    @Override public void inject(TraceContext traceContext, R request) {
      delegate.inject(traceContext, request);
      ExtraBaggageFields extra = traceContext.findExtra(ExtraBaggageFields.class);
      if (extra == null) return;
      inject(extra, request);
    }

    void inject(ExtraBaggageFields extraBaggageFields, R request) {
      for (RemoteHandlerWithKeys<K> handlerWithKeys : propagation.remoteHandlersWithKeys) {
        String value = extraBaggageFields.getRemoteValue(handlerWithKeys.handler);
        if (value == null) continue;
        for (K key : handlerWithKeys.keys) setter.put(request, key, value);
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

      if (propagation.factory.allKeyNames == null) return builder.build();

      for (RemoteHandlerWithKeys<K> handlerWithKeys : propagation.remoteHandlersWithKeys) {
        for (K key : handlerWithKeys.keys) { // possibly multiple keys when prefixes are in use
          String value = getter.get(request, key);
          if (value != null) { // accept the first match
            if (extra.putRemoteValue(handlerWithKeys.handler, request, value)) {
              break;
            }
          }
        }
      }

      return builder.addExtra(propagation.factory.allKeyNames).build();
    }
  }
}

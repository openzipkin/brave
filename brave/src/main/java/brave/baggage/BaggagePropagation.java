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

import brave.internal.baggage.BaggageHandler;
import brave.internal.baggage.BaggageHandlers;
import brave.internal.baggage.ExtraBaggageFields;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static brave.baggage.BaggageField.validateName;

/**
 * This implements in-process and remote {@linkplain BaggageField baggage} propagation.
 *
 * <p>For example, if you have a need to know the a specific request's country code, you can
 * propagate it through the trace as HTTP headers.
 * <pre>{@code
 * // Configure your baggage field
 * COUNTRY_CODE = BaggageField.create("country-code");
 *
 * // When you initialize the builder, add the baggage you want to propagate
 * tracingBuilder.propagationFactory(
 *   BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY)
 *                     .addRemoteField(COUNTRY_CODE)
 *                     .build()
 * );
 *
 * // later, you can tag that country code
 * Tags.BAGGAGE_FIELD.tag(COUNTRY_CODE, span);
 * }</pre>
 *
 * <h3>Customizing propagtion keys</h3>
 * By default, the name used as a propagation key (header) by {@link
 * FactoryBuilder#addRemoteField(BaggageField, String...)} is the same as the lowercase variant of
 * the field name. You can override this by supplying different key names. Note: they will be
 * lower-cased.
 *
 * <p>For example, the following will propagate the field "x-vcap-request-id" as-is, but send the
 * fields "countryCode" and "userId" on the wire as "baggage-country-code" and "baggage-user-id"
 * respectively.
 *
 * <pre>{@code
 * REQUEST_ID = BaggageField.create("x-vcap-request-id");
 * COUNTRY_CODE = BaggageField.create("countryCode");
 * USER_ID = BaggageField.create("userId");
 *
 * tracingBuilder.propagationFactory(
 *     BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY)
 *                       .addRemoteField(REQUEST_ID)
 *                       .addRemoteField(COUNTRY_CODE, "baggage-country-code")
 *                       .addRemoteField(USER_ID, "baggage-user-id").build())
 * );
 * }</pre>
 *
 * <p>See {@link BaggageField} for usage examples
 *
 * @see BaggageField
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
    final Set<String> allKeyNames = new LinkedHashSet<>();
    final Map<BaggageField, Set<String>> fieldToKeyNames = new LinkedHashMap<>();

    FactoryBuilder(Propagation.Factory delegate) {
      if (delegate == null) throw new NullPointerException("delegate == null");
      this.delegate = delegate;
    }

    /**
     * Returns an immutable copy of the currently configured fields mapped to names for use in
     * remote propagation. This allows those who can't create the builder to reconfigure this
     * builder.
     *
     * @since 5.11
     */
    public Map<BaggageField, Set<String>> fieldToKeyNames() {
      return Collections.unmodifiableMap(new LinkedHashMap<>(fieldToKeyNames));
    }

    /**
     * Clears all state. This allows those who can't create the builder to reconfigure fields.
     *
     * @see #fieldToKeyNames()
     * @see BaggagePropagationCustomizer
     * @since 5.11
     */
    public FactoryBuilder clear() {
      allKeyNames.clear();
      fieldToKeyNames.clear();
      return this;
    }

    /**
     * Adds a {@linkplain BaggageField baggage field}, but does not configure remote propagation.
     *
     * @throws IllegalArgumentException if the field was already added
     * @since 5.11
     */
    public FactoryBuilder addField(BaggageField field) {
      if (field == null) throw new NullPointerException("field == null");
      if (fieldToKeyNames.containsKey(field)) {
        throw new IllegalArgumentException(field.name + " already added");
      }
      fieldToKeyNames.put(field, Collections.emptySet());
      return this;
    }

    /**
     * Adds a {@linkplain BaggageField baggage field} for remote propagation.
     *
     * <p>When {@code keyNames} are not supplied the field is referenced the same in-process as it
     * is on the wire. For example, the {@linkplain BaggageField#name() name} "x-vcap-request-id"
     * would be set as-is including the prefix.
     *
     * @param keyNames possibly empty lower-case {@link Propagation#keys() propagation key names}.
     * @throws IllegalArgumentException if the field was already added or a key name is already in
     * use.
     * @since 5.11
     */
    public FactoryBuilder addRemoteField(BaggageField field, String... keyNames) {
      if (field == null) throw new NullPointerException("field == null");
      if (keyNames == null) throw new NullPointerException("keyNames == null");
      return addRemoteField(field, Arrays.asList(keyNames));
    }

    /**
     * Same as {@link #addRemoteField(BaggageField, String...)}.
     *
     * @since 5.11
     */
    public FactoryBuilder addRemoteField(BaggageField field, Iterable<String> keyNames) {
      if (field == null) throw new NullPointerException("field == null");
      if (keyNames == null) throw new NullPointerException("keyNames == null");
      if (fieldToKeyNames.containsKey(field)) {
        throw new IllegalArgumentException(field.name + " already added");
      }
      Set<String> lcKeyNames = new LinkedHashSet<>();
      for (String keyName : keyNames) {
        String lcName = validateName(keyName).toLowerCase(Locale.ROOT);
        if (allKeyNames.contains(lcName)) {
          throw new IllegalArgumentException("Propagation key already in use: " + lcName);
        }
        allKeyNames.add(lcName);
        lcKeyNames.add(lcName);
      }

      if (lcKeyNames.isEmpty()) { // add the default name
        allKeyNames.add(field.lcName);
        lcKeyNames.add(field.lcName);
      }

      fieldToKeyNames.put(field, Collections.unmodifiableSet(lcKeyNames));
      return this;
    }

    /** Returns the delegate if there are no fields to propagate. */
    public Propagation.Factory build() {
      if (fieldToKeyNames.isEmpty()) return delegate;
      BaggageHandlerWithKeyNames[] handlersWithKeyNames =
        new BaggageHandlerWithKeyNames[fieldToKeyNames.size()];
      int i = 0;
      for (Map.Entry<BaggageField, Set<String>> entry : fieldToKeyNames.entrySet()) {
        // one state entry per baggage field, for now..
        BaggageHandler<String> handler = BaggageHandlers.string(entry.getKey());
        handlersWithKeyNames[i++] = new BaggageHandlerWithKeyNames(
          handler, entry.getValue().toArray(new String[0])
        );
      }
      return new Factory(delegate, handlersWithKeyNames);
    }
  }

  /** For {@link Propagation.Factory} */
  static final class BaggageHandlerWithKeyNames {
    final BaggageHandler handler;
    final String[] keyNames;

    BaggageHandlerWithKeyNames(BaggageHandler handler, String[] keyNames) {
      this.handler = handler;
      this.keyNames = keyNames;
    }
  }

  /** For {@link Propagation.Factory#create(KeyFactory)} */
  static final class BaggageHandlerWithKeys<K> {
    final BaggageHandler handler;
    final K[] keys;

    BaggageHandlerWithKeys(BaggageHandler handler, K[] keys) {
      this.handler = handler;
      this.keys = keys;
    }
  }

  static final class Factory extends Propagation.Factory {
    final Propagation.Factory delegate;
    final BaggageHandlerWithKeyNames[] handlersWithKeyNames;
    final ExtraBaggageFields.Factory stateFactory;

    Factory(Propagation.Factory delegate, BaggageHandlerWithKeyNames[] handlersWithKeyNames) {
      this.delegate = delegate;
      this.handlersWithKeyNames = handlersWithKeyNames;
      BaggageHandler[] handlers = new BaggageHandler[handlersWithKeyNames.length];
      for (int i = 0, length = handlersWithKeyNames.length; i < length; i++) {
        handlers[i] = handlersWithKeyNames[i].handler;
      }
      this.stateFactory = ExtraBaggageFields.newFactory(handlers);
    }

    @Override
    public final <K> BaggagePropagation<K> create(Propagation.KeyFactory<K> keyFactory) {
      BaggageHandlerWithKeys<K>[] handlersWithKeys =
        new BaggageHandlerWithKeys[handlersWithKeyNames.length];
      int i = 0;
      for (BaggageHandlerWithKeyNames next : handlersWithKeyNames) {
        int length = next.keyNames.length;
        K[] keysForField = (K[]) new Object[next.keyNames.length];
        for (int j = 0; j < length; j++) {
          keysForField[j] = keyFactory.create(next.keyNames[j]);
        }
        handlersWithKeys[i++] = new BaggageHandlerWithKeys<>(next.handler, keysForField);
      }
      return new BaggagePropagation<>(this, keyFactory, handlersWithKeys);
    }

    @Override public TraceContext decorate(TraceContext context) {
      TraceContext result = delegate.decorate(context);
      return stateFactory.decorate(result);
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
  final BaggageHandlerWithKeys<K>[] handlersWithKeys;

  BaggagePropagation(Factory factory, Propagation.KeyFactory<K> keyFactory,
    BaggageHandlerWithKeys<K>[] handlersWithKeys) {
    this.delegate = factory.delegate.create(keyFactory);
    this.factory = factory;
    this.handlersWithKeys = handlersWithKeys;
  }

  /**
   * Only returns trace context keys. Baggage field names are not returned to ensure tools don't
   * delete them. This is to support users accessing baggage without Brave apis (ex via headers).
   */
  @Override public List<K> keys() {
    return delegate.keys();
  }

  @Override public <C> Injector<C> injector(Setter<C, K> setter) {
    return new BaggageInjector<>(this, setter);
  }

  @Override public <C> Extractor<C> extractor(Getter<C, K> getter) {
    return new BaggageExtractor<>(this, getter);
  }

  static final class BaggageInjector<C, K> implements Injector<C> {
    final BaggagePropagation<K> propagation;
    final Injector<C> delegate;
    final Propagation.Setter<C, K> setter;

    BaggageInjector(BaggagePropagation<K> propagation, Setter<C, K> setter) {
      this.propagation = propagation;
      this.delegate = propagation.delegate.injector(setter);
      this.setter = setter;
    }

    @Override public void inject(TraceContext traceContext, C carrier) {
      delegate.inject(traceContext, carrier);
      ExtraBaggageFields extra = traceContext.findExtra(ExtraBaggageFields.class);
      if (extra == null) return;
      inject(extra, carrier);
    }

    void inject(ExtraBaggageFields extraBaggageFields, C carrier) {
      for (int i = 0, length = propagation.handlersWithKeys.length; i < length; i++) {
        BaggageHandlerWithKeys<K> handlerWithKeys = propagation.handlersWithKeys[i];
        String encoded = extraBaggageFields.getRemoteValue(handlerWithKeys.handler);
        if (encoded == null) continue;
        for (K key : handlerWithKeys.keys) setter.put(carrier, key, encoded);
      }
    }
  }

  static final class BaggageExtractor<C, K> implements Extractor<C> {
    final BaggagePropagation<K> propagation;
    final Extractor<C> delegate;
    final Propagation.Getter<C, K> getter;

    BaggageExtractor(BaggagePropagation<K> propagation, Getter<C, K> getter) {
      this.propagation = propagation;
      this.delegate = propagation.delegate.extractor(getter);
      this.getter = getter;
    }

    @Override public TraceContextOrSamplingFlags extract(C carrier) {
      TraceContextOrSamplingFlags result = delegate.extract(carrier);

      // always allocate in case values are added late
      ExtraBaggageFields extraBaggageFields = propagation.factory.stateFactory.create();
      for (int i = 0, length = propagation.handlersWithKeys.length; i < length; i++) {
        BaggageHandlerWithKeys<K> handlerWithKeys = propagation.handlersWithKeys[i];
        for (K key : handlerWithKeys.keys) { // possibly multiple keys when prefixes are in use
          String maybeEncoded = getter.get(carrier, key);
          if (maybeEncoded != null) { // accept the first match
            if (extraBaggageFields.putRemoteValue(handlerWithKeys.handler, maybeEncoded)) break;
          }
        }
      }

      return result.toBuilder().addExtra(extraBaggageFields).build();
    }
  }
}

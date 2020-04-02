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
 *                     .addField(COUNTRY_CODE)
 *                     .build()
 * );
 *
 * // later, you can tag that country code
 * Tags.BAGGAGE_FIELD.tag(COUNTRY_CODE, span);
 * }</pre>
 *
 * @since 5.11
 */
public class BaggagePropagation<K> implements Propagation<K> {
  /** Wraps an underlying propagation implementation, pushing one or more fields. */
  public static FactoryBuilder newFactoryBuilder(Propagation.Factory delegate) {
    return new FactoryBuilder(delegate);
  }

  public static class FactoryBuilder { // not final to backport ExtraFieldPropagation
    final Propagation.Factory delegate;
    final Set<BaggageField> fields = new LinkedHashSet<>();

    FactoryBuilder(Propagation.Factory delegate) {
      if (delegate == null) throw new NullPointerException("delegate == null");
      this.delegate = delegate;
    }

    /**
     * Returns an immutable copy of the currently configured fields. This allows those who can't
     * create the builder to reconfigure fields.
     *
     * @see #clear()
     * @see BaggagePropagationCustomizer
     * @since 5.11
     */
    public List<BaggageField> fields() {
      return Collections.unmodifiableList(new ArrayList<>(fields));
    }

    /**
     * Clears all state. This allows those who can't create the builder to reconfigure fields.
     *
     * @see #fields()
     * @see BaggagePropagationCustomizer
     * @since 5.11
     */
    public FactoryBuilder clear() {
      fields.clear();
      return this;
    }

    /**
     * Adds a {@linkplain BaggageField baggage field} for remote propagation.
     *
     * @since 5.11
     */
    public FactoryBuilder addField(BaggageField field) {
      if (field == null) throw new NullPointerException("field == null");
      fields.add(field);
      return this;
    }

    /** Returns the delegate if there are no fields to propagate. */
    public Propagation.Factory build() {
      if (fields.isEmpty()) return delegate;

      // check for duplicate remote names
      Map<String, Set<String>> remoteNameToFields = new LinkedHashMap<>();
      for (BaggageField field : fields) {
        for (String remoteName : field.remoteNames()) {
          Set<String> fields = remoteNameToFields.get(remoteName);
          if (fields == null) remoteNameToFields.put(remoteName, fields = new LinkedHashSet<>());
          fields.add(field.name());
        }
      }

      for (Entry<String, Set<String>> entry : remoteNameToFields.entrySet()) {
        if (entry.getValue().size() > 1) {
          throw new UnsupportedOperationException( // Later, we will support this!
            entry.getValue() + " have the same remote name: " + entry.getKey());
        }
      }

      return new Factory(delegate, fields.toArray(new BaggageField[0]));
    }
  }

  static class BaggageFieldWithKeys<K> {
    final BaggageField field;
    /** Corresponds to {@link BaggageField#remoteNames()} */
    final K[] keys;

    BaggageFieldWithKeys(BaggageField field, K[] keys) {
      this.field = field;
      this.keys = keys;
    }
  }

  static final class Factory extends Propagation.Factory {
    final PredefinedBaggageFields.Factory extraFactory;
    final Propagation.Factory delegate;

    Factory(Propagation.Factory delegate, BaggageField[] fields) {
      this.delegate = delegate;
      this.extraFactory = new PredefinedBaggageFields.Factory(fields);
    }

    @Override
    public final <K> BaggagePropagation<K> create(Propagation.KeyFactory<K> keyFactory) {
      int i = 0;
      BaggageFieldWithKeys<K>[] fieldsWithKeys =
        new BaggageFieldWithKeys[extraFactory.fields.length];
      for (BaggageField field : extraFactory.fields) {
        K[] keysForField = (K[]) new Object[field.remoteNames.length];
        for (int j = 0, length = field.remoteNames.length; j < length; j++) {
          keysForField[j] = keyFactory.create(field.remoteNames[j]);
        }
        fieldsWithKeys[i++] = new BaggageFieldWithKeys<>(field, keysForField);
      }
      return new BaggagePropagation<>(this, keyFactory, fieldsWithKeys);
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
  final BaggageFieldWithKeys<K>[] fieldsWithKeys;

  BaggagePropagation(Factory factory, Propagation.KeyFactory<K> keyFactory,
    BaggageFieldWithKeys<K>[] fieldsWithKeys) {
    this.delegate = factory.delegate.create(keyFactory);
    this.factory = factory;
    this.fieldsWithKeys = fieldsWithKeys;
  }

  /**
   * Only returns trace context keys. Baggage field names are not returned to ensure tools don't
   * delete them. This is to support users accessing baggage without Brave apis (ex via headers).
   */
  @Override public List<K> keys() {
    return delegate.keys();
  }

  @Override public <C> Injector<C> injector(Setter<C, K> setter) {
    return new BaggageFieldInjector<>(this, setter);
  }

  @Override public <C> Extractor<C> extractor(Getter<C, K> getter) {
    return new BaggageFieldExtractor<>(this, getter);
  }

  static final class BaggageFieldInjector<C, K> implements Injector<C> {
    final BaggagePropagation<K> propagation;
    final Injector<C> delegate;
    final Propagation.Setter<C, K> setter;

    BaggageFieldInjector(BaggagePropagation<K> propagation, Setter<C, K> setter) {
      this.propagation = propagation;
      this.delegate = propagation.delegate.injector(setter);
      this.setter = setter;
    }

    @Override public void inject(TraceContext traceContext, C carrier) {
      delegate.inject(traceContext, carrier);
      PredefinedBaggageFields extra = traceContext.findExtra(PredefinedBaggageFields.class);
      if (extra == null) return;
      inject(extra, carrier);
    }

    void inject(PredefinedBaggageFields fields, C carrier) {
      for (BaggageFieldWithKeys<K> fieldWithKeys : propagation.fieldsWithKeys) {
        String maybeValue = fields.get(fieldWithKeys.field);
        if (maybeValue == null) continue;
        for (K key : fieldWithKeys.keys) setter.put(carrier, key, maybeValue);
      }
    }
  }

  static final class BaggageFieldExtractor<C, K> implements Extractor<C> {
    final BaggagePropagation<K> propagation;
    final Extractor<C> delegate;
    final Propagation.Getter<C, K> getter;

    BaggageFieldExtractor(BaggagePropagation<K> propagation, Getter<C, K> getter) {
      this.propagation = propagation;
      this.delegate = propagation.delegate.extractor(getter);
      this.getter = getter;
    }

    @Override public TraceContextOrSamplingFlags extract(C carrier) {
      TraceContextOrSamplingFlags result = delegate.extract(carrier);

      // always allocate in case values are added late
      PredefinedBaggageFields fields = propagation.factory.extraFactory.create();
      for (BaggageFieldWithKeys<K> fieldWithKeys : propagation.fieldsWithKeys) {
        for (K key : fieldWithKeys.keys) { // possibly multiple keys when prefixes are in use
          String maybeValue = getter.get(carrier, key);
          if (maybeValue != null) { // accept the first match
            fields.put(fieldWithKeys.field, maybeValue);
            break;
          }
        }
      }

      return result.toBuilder().addExtra(fields).build();
    }
  }
}

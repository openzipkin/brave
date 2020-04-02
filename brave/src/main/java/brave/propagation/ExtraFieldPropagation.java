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

import brave.baggage.BaggageField;
import brave.baggage.BaggagePropagation;
import brave.internal.Nullable;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.unmodifiableList;

/** @deprecated Since 5.11 use {@link BaggagePropagation} */
@Deprecated public class ExtraFieldPropagation<K> implements Propagation<K> {
  /** @deprecated Since 5.11 use {@link BaggagePropagation#newFactoryBuilder(Propagation.Factory)} */
  @Deprecated public static Factory newFactory(Propagation.Factory delegate, String... names) {
    if (delegate == null) throw new NullPointerException("delegate == null");
    if (names == null) throw new NullPointerException("names == null");
    return newFactory(delegate, Arrays.asList(names));
  }

  /** @deprecated Since 5.11 use {@link BaggagePropagation#newFactoryBuilder(Propagation.Factory)} */
  @Deprecated public static Factory newFactory(Propagation.Factory delegate,
    Collection<String> names) {
    if (delegate == null) throw new NullPointerException("delegate == null");
    if (names == null) throw new NullPointerException("field names == null");
    if (names.isEmpty()) throw new IllegalArgumentException("no field names");
    FactoryBuilder builder = new FactoryBuilder(delegate);
    for (String name : names) builder.addField(name);
    return builder.build();
  }

  /** @deprecated Since 5.11 use {@link BaggagePropagation#newFactoryBuilder(Propagation.Factory)} */
  @Deprecated public static FactoryBuilder newFactoryBuilder(Propagation.Factory delegate) {
    return new FactoryBuilder(delegate);
  }

  /** @deprecated Since 5.11 use {@link BaggagePropagation.FactoryBuilder} */
  @Deprecated public static final class FactoryBuilder {
    final Propagation.Factory delegate;
    final BaggagePropagation.FactoryBuilder baggageFactory;
    // Updates could be out-of-order in the old impl so we track everything until build()
    final Set<String> redactedNames = new LinkedHashSet<>();
    final Map<String, Set<String>> nameToKeyNames = new LinkedHashMap<>();

    FactoryBuilder(Propagation.Factory delegate) {
      this.delegate = delegate;
      this.baggageFactory = BaggagePropagation.newFactoryBuilder(delegate);
    }

    /**
     * @deprecated Since 5.11,  use {@link BaggagePropagation.FactoryBuilder#addField(BaggageField)}
     */
    @Deprecated public FactoryBuilder addRedactedField(String fieldName) {
      fieldName = validateFieldName(fieldName);
      redactedNames.add(fieldName);
      nameToKeyNames.put(fieldName, Collections.emptySet());
      return this;
    }

    /**
     * @deprecated Since 5.11, use {@link BaggagePropagation.FactoryBuilder#addRemoteField(BaggageField,
     * String...)}.
     */
    @Deprecated public FactoryBuilder addField(String fieldName) {
      fieldName = validateFieldName(fieldName);
      addKeyName(fieldName, fieldName);
      return this;
    }

    /**
     * @deprecated Since 5.11, use {@link BaggagePropagation.FactoryBuilder#addRemoteField(BaggageField,
     * Iterable)}
     */
    @Deprecated public FactoryBuilder addPrefixedFields(String prefix, Collection<String> names) {
      if (prefix == null) throw new NullPointerException("prefix == null");
      prefix = validateFieldName(prefix);
      if (names == null) throw new NullPointerException("names == null");
      for (String name : names) {
        name = validateFieldName(name);
        addKeyName(name, prefix + name);
      }
      return this;
    }

    void addKeyName(String name, String keyName) {
      Set<String> keyNames = nameToKeyNames.get(name);
      if (keyNames == null) nameToKeyNames.put(name, keyNames = new LinkedHashSet<>());
      keyNames.add(keyName);
    }

    /** Returns a wrapper of the delegate if there are no fields to propagate. */
    public Factory build() {
      Set<String> extraKeyNames = new LinkedHashSet<>();
      for (Map.Entry<String, Set<String>> entry : nameToKeyNames.entrySet()) {
        BaggageField field = BaggageField.create(entry.getKey());
        if (redactedNames.contains(field.name())) {
          baggageFactory.addField(field);
        } else {
          extraKeyNames.addAll(entry.getValue());
          baggageFactory.addRemoteField(field, entry.getValue());
        }
      }
      return new Factory(baggageFactory.build(), extraKeyNames.toArray(new String[0]));
    }
  }

  /**
   * @deprecated Since 5.11 use {@link BaggageField#getByName(String)} and {@link
   * BaggageField#getValue()}
   */
  @Deprecated @Nullable public static String current(String name) {
    return get(name);
  }

  /**
   * @deprecated Since 5.11 use {@link BaggageField#getByName(String)} and {@link
   * BaggageField#getValue()}
   */
  @Deprecated @Nullable public static String get(String name) {
    BaggageField field = BaggageField.getByName(validateFieldName(name));
    if (field == null) return null;
    return field.getValue();
  }

  /**
   * @deprecated Since 5.11 use {@link BaggageField#getByName(String)} and {@link
   * BaggageField#updateValue(String)}
   */
  @Deprecated public static void set(String name, String value) {
    BaggageField field = BaggageField.getByName(validateFieldName(name));
    if (field == null) return;
    field.updateValue(value);
  }

  /** @deprecated Since 5.11 use {@link BaggageField#getAll()} */
  @Deprecated public static Map<String, String> getAll() {
    return getAll(BaggageField.getAll(), null);
  }

  /** @deprecated Since 5.11 use {@link BaggageField#getAll(TraceContextOrSamplingFlags)} */
  @Deprecated public static Map<String, String> getAll(TraceContextOrSamplingFlags extracted) {
    if (extracted.context() != null) return getAll(extracted.context());
    Map<String, String> result = new LinkedHashMap<>();
    for (BaggageField field : BaggageField.getAll(extracted)) {
      String value = field.getValue(extracted);
      if (value != null) result.put(field.name(), value);
    }
    return result;
  }

  /** @deprecated Since 5.11 use {@link BaggageField#getAll(TraceContext)} */
  @Deprecated public static Map<String, String> getAll(TraceContext context) {
    return getAll(BaggageField.getAll(context), context);
  }

  /**
   * @deprecated Since 5.11 use {@link BaggageField#getByName(TraceContext, String)} and {@link
   * BaggageField#getValue(TraceContext)}
   */
  @Deprecated @Nullable public static String get(TraceContext context, String name) {
    BaggageField field = BaggageField.getByName(context, validateFieldName(name));
    if (field == null) return null;
    return field.getValue(context);
  }

  /**
   * @deprecated Since 5.11 use {@link BaggageField#getByName(TraceContext, String)} and {@link
   * BaggageField#updateValue(String)}
   */
  @Deprecated public static void set(TraceContext context, String name, String value) {
    BaggageField field = BaggageField.getByName(context, validateFieldName(name));
    if (field == null) return;
    field.updateValue(context, value);
  }

  /** @deprecated Since 5.11 use {@link Propagation.Factory} */
  public static class Factory extends Propagation.Factory {
    final Propagation.Factory delegate;
    final String[] extraKeyNames;

    Factory(Propagation.Factory delegate, String[] extraKeyNames) {
      this.delegate = delegate;
      this.extraKeyNames = extraKeyNames;
    }

    @Override public <K> ExtraFieldPropagation<K> create(Propagation.KeyFactory<K> keyFactory) {
      List<K> extraKeys = new ArrayList<>();
      for (String extraKeyName : extraKeyNames) extraKeys.add(keyFactory.create(extraKeyName));
      return new ExtraFieldPropagation<>(delegate.create(keyFactory), unmodifiableList(extraKeys));
    }

    @Override public boolean supportsJoin() {
      return delegate.supportsJoin();
    }

    @Override public boolean requires128BitTraceId() {
      return delegate.requires128BitTraceId();
    }

    @Override public TraceContext decorate(TraceContext context) {
      return delegate.decorate(context);
    }
  }

  final Propagation<K> delegate;
  final List<K> extraKeys;

  ExtraFieldPropagation(Propagation<K> delegate, List<K> extraKeys) {
    this.delegate = delegate;
    this.extraKeys = extraKeys;
  }

  /**
   * Returns the extra keys this component can extract. This result is lowercase and does not
   * include any {@link #keys() trace context keys}.
   *
   * @deprecated Since 5.11 do not use this, as it was only added for OpenTracing. brave-opentracing
   * works around this.
   */
  @Deprecated public List<K> extraKeys() {
    return extraKeys;
  }

  /**
   * Only returns trace context keys. Extra field names are not returned to ensure tools don't
   * delete them. This is to support users accessing extra fields without Brave apis (ex via
   * headers).
   */
  @Override public List<K> keys() {
    return delegate.keys();
  }

  @Override public <C> Injector<C> injector(Setter<C, K> setter) {
    return delegate.injector(setter);
  }

  @Override public <C> Extractor<C> extractor(Getter<C, K> getter) {
    return delegate.extractor(getter);
  }

  static String validateFieldName(String fieldName) {
    if (fieldName == null) throw new NullPointerException("fieldName == null");
    fieldName = fieldName.toLowerCase(Locale.ROOT).trim();
    if (fieldName.isEmpty()) throw new IllegalArgumentException("fieldName is empty");
    return fieldName;
  }

  static Map<String, String> getAll(List<BaggageField> fields, @Nullable TraceContext context) {
    Map<String, String> result = new LinkedHashMap<>();
    for (BaggageField field : fields) {
      String value = context != null ? field.getValue(context) : field.getValue();
      if (value != null) result.put(field.name(), value);
    }
    return result;
  }
}

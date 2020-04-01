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

import brave.internal.Nullable;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
    for (String name : names) {
      builder.addField(BaggageField.create(validateFieldName(name)));
    }
    return builder.build();
  }

  /** @deprecated Since 5.11 use {@link BaggagePropagation#newFactoryBuilder(Propagation.Factory)} */
  @Deprecated public static FactoryBuilder newFactoryBuilder(Propagation.Factory delegate) {
    return new FactoryBuilder(delegate);
  }

  /** @deprecated Since 5.11 use {@link BaggagePropagation.FactoryBuilder} */
  @Deprecated public static final class FactoryBuilder extends BaggagePropagation.FactoryBuilder {
    final Set<String> names = new LinkedHashSet<>();
    final Set<String> redactedNames = new LinkedHashSet<>();
    final Map<String, Set<String>> nameToPrefixes = new LinkedHashMap<>();

    FactoryBuilder(Propagation.Factory delegate) {
      super(delegate);
    }

    /**
     * @deprecated Since 5.11,  use {@link #addField(BaggageField)} with a baggage field built with
     * {@link BaggageField.Builder#clearRemoteNames()}.
     */
    @Deprecated public FactoryBuilder addRedactedField(String fieldName) {
      fieldName = validateFieldName(fieldName);
      names.add(fieldName);
      redactedNames.add(fieldName);
      return this;
    }

    /**
     * @deprecated Since 5.11, use {@link #addField(BaggageField)} with {@link
     * BaggageField.Builder#create(String)}.
     */
    @Deprecated public FactoryBuilder addField(String fieldName) {
      names.add(validateFieldName(fieldName));
      return this;
    }

    /**
     * @deprecated Since 5.11, use {@link #addField(BaggageField)} with a baggage field built with
     * {@link BaggageField.Builder#clearRemoteNames()} and {@linkplain
     * BaggageField.Builder#addRemoteName(String) add the prefixed name explicitly}.
     */
    @Deprecated public FactoryBuilder addPrefixedFields(String prefix, Collection<String> names) {
      if (prefix == null) throw new NullPointerException("prefix == null");
      prefix = validateFieldName(prefix);
      if (names == null) throw new NullPointerException("names == null");
      for (String name : names) {
        name = validateFieldName(name);
        Set<String> prefixes = nameToPrefixes.get(name);
        if (prefixes == null) nameToPrefixes.put(name, prefixes = new LinkedHashSet<>());
        prefixes.add(prefix);
      }
      return this;
    }

    Set<BaggageField> convertDeprecated() {
      Set<String> remainingNames = new LinkedHashSet<>(names);
      Set<BaggageField> result = new LinkedHashSet<>();
      for (Map.Entry<String, Set<String>> entry : nameToPrefixes.entrySet()) {
        String name = entry.getKey();
        if (redactedNames.contains(name)) continue;

        BaggageField.Builder builder = BaggageField.newBuilder(name);
        // If we didn't add the name directly, we should only add prefixed names.
        if (!remainingNames.remove(name)) builder.clearRemoteNames();
        for (String prefix : entry.getValue()) {
          builder.addRemoteName(prefix + name);
        }
        result.add(builder.build());
      }

      for (String name : remainingNames) {
        BaggageField.Builder builder = BaggageField.newBuilder(name);
        if (redactedNames.contains(name)) builder.clearRemoteNames();
        result.add(builder.build());
      }
      return result;
    }

    /** Returns a wrapper of the delegate if there are no fields to propagate. */
    public Factory build() {
      Set<BaggageField> fields = convertDeprecated();
      fields.addAll(this.fields); // clobbering deprecated config is ok
      BaggageField[] fieldsArray = fields.toArray(new BaggageField[0]);
      if (fieldsArray.length == 0) {
        return new Factory(delegate, fieldsArray);
      }
      return new Factory(new BaggagePropagation.Factory(delegate, fieldsArray), fieldsArray);
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
    final BaggageField[] fields;

    Factory(Propagation.Factory delegate, BaggageField[] fields) {
      this.delegate = delegate;
      this.fields = fields;
    }

    @Override public <K> ExtraFieldPropagation<K> create(Propagation.KeyFactory<K> keyFactory) {
      List<K> keys = new ArrayList<>();
      for (BaggageField field : fields) {
        for (String remoteName : field.remoteNames) {
          keys.add(keyFactory.create(remoteName));
        }
      }
      return new ExtraFieldPropagation<>(delegate.create(keyFactory), keys);
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

  /** @deprecated Since 5.11 use {@link BaggageField#getAll(TraceContext)} */
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

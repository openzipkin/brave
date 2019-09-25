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
package brave.internal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Copy-on-write keeps propagation changes in a child context from affecting its parent.
 *
 * <p>Type parameters must be immutable to ensure copy-on-write branch safety. For example, if they
 * weren't, modifications by a child span could effect its parent and siblings.
 *
 * @param <K> Must be immutable to ensure copy-on-write is effective.
 * @param <V> Must be immutable to ensure copy-on-write is effective.
 */
public class MapPropagationFields<K, V> extends PropagationFields<K, V> {
  volatile Map<K, V> values; // guarded by this, copy on write

  protected MapPropagationFields() {
  }

  protected MapPropagationFields(Map<K, V> parent) {
    if (parent == null) throw new NullPointerException("parent == null");
    this.values = Collections.unmodifiableMap(parent);
  }

  protected MapPropagationFields(MapPropagationFields<K, V> parent) {
    this.values = parent.values;
  }

  @Override public V get(K key) {
    Map<K, V> values = this.values;
    return values != null ? values.get(key) : null;
  }

  @Override public void forEach(FieldConsumer<K, V> fieldConsumer) {
    synchronized (this) {
      Map<K, V> values = this.values;
      if (values == null) return;

      for (Map.Entry<K, V> entry : values.entrySet()) {
        V value = entry.getValue();
        if (value == null) continue;
        fieldConsumer.accept(entry.getKey(), value);
      }
    }
  }

  @Override public final void put(K key, V value) {
    synchronized (this) {
      Map<K, V> values = this.values;
      if (values == null) {
        values = new LinkedHashMap<>();
        values.put(key, value);
      } else if (value.equals(values.get(key))) {
        return;
      } else {
        // this is the copy-on-write part
        values = new LinkedHashMap<>(values);
        values.put(key, value);
      }
      this.values = Collections.unmodifiableMap(values);
    }
  }

  @Override public boolean isEmpty() {
    Map<K, V> values = this.values;
    return values == null || values.isEmpty();
  }

  @Override protected final void putAllIfAbsent(PropagationFields parent) {
    if (!(parent instanceof MapPropagationFields)) return;
    MapPropagationFields<K, V> mapParent = (MapPropagationFields<K, V>) parent;
    Map<K, V> parentValues = mapParent.values;
    if (parentValues == null) return;

    synchronized (this) {
      if (values == null) {
        values = parentValues;
        return;
      }
    }

    for (Map.Entry<K, V> entry : parentValues.entrySet()) {
      if (values.containsKey(entry.getKey())) continue; // previous wins vs parent
      put(entry.getKey(), entry.getValue());
    }
  }

  @Override public final Map<K, V> toMap() {
    Map<K, V> values = this.values;
    if (values == null) return Collections.emptyMap();
    return values;
  }

  @Override public int hashCode() { // for unit tests
    Map<K, V> values = this.values;
    return values == null ? 0 : values.hashCode();
  }

  @Override public boolean equals(Object o) { // for unit tests
    if (o == this) return true;
    if (!(o instanceof MapPropagationFields)) return false;
    MapPropagationFields<K, V> that = (MapPropagationFields) o;
    Map<K, V> values = this.values, thatValues = that.values;
    return values == null ? thatValues == null : values.equals(thatValues);
  }
}

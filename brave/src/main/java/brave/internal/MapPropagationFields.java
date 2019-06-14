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

/** Copy-on-write keeps propagation changes in a child context from affecting its parent */
public class MapPropagationFields extends PropagationFields {

  volatile Map<String, String> values; // guarded by this, copy on write

  protected MapPropagationFields() {
  }

  protected MapPropagationFields(Map<String, String> parent) {
    this.values = Collections.unmodifiableMap(parent);
  }

  protected MapPropagationFields(MapPropagationFields parent) {
    this.values = parent.values;
  }

  @Override public String get(String name) {
    Map<String, String> values;
    synchronized (this) {
      values = this.values;
    }
    return values != null ? values.get(name) : null;
  }

  @Override public final void put(String name, String value) {
    synchronized (this) {
      Map<String, String> values = this.values;
      if (values == null) {
        values = new LinkedHashMap<>();
        values.put(name, value);
      } else if (value.equals(values.get(name))) {
        return;
      } else {
        // this is the copy-on-write part
        values = new LinkedHashMap<>(values);
        values.put(name, value);
      }
      this.values = Collections.unmodifiableMap(values);
    }
  }

  @Override protected final void putAllIfAbsent(PropagationFields parent) {
    if (!(parent instanceof MapPropagationFields)) return;
    MapPropagationFields mapParent = (MapPropagationFields) parent;
    Map<String, String> parentValues = mapParent.values;
    if (parentValues == null) return;

    synchronized (this) {
      if (values == null) {
        values = parentValues;
        return;
      }
    }

    for (Map.Entry<String, String> entry : parentValues.entrySet()) {
      if (values.containsKey(entry.getKey())) continue; // previous wins vs parent
      put(entry.getKey(), entry.getValue());
    }
  }

  @Override public final Map<String, String> toMap() {
    Map<String, String> values;
    synchronized (this) {
      values = this.values;
    }
    if (values == null) return Collections.emptyMap();
    return values;
  }

  @Override public int hashCode() { // for unit tests
    return values == null ? 0 : values.hashCode();
  }

  @Override public boolean equals(Object o) { // for unit tests
    if (o == this) return true;
    if (!(o instanceof MapPropagationFields)) return false;
    MapPropagationFields that = (MapPropagationFields) o;
    return values == null ? that.values == null : values.equals(that.values);
  }
}

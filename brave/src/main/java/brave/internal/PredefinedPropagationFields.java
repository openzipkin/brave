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

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Copy-on-write keeps propagation changes in a child context from affecting its parent */
public class PredefinedPropagationFields extends PropagationFields {

  final String[] fieldNames;
  volatile String[] values; // guarded by this, copy on write

  protected PredefinedPropagationFields(String... fieldNames) {
    this.fieldNames = fieldNames;
  }

  protected PredefinedPropagationFields(PredefinedPropagationFields parent, String... fieldNames) {
    this.fieldNames = fieldNames;
    checkSameFields(parent);
    this.values = parent.values;
  }

  @Override public String get(String name) {
    int index = indexOf(name);
    return index != -1 ? get(index) : null;
  }

  public String get(int index) {
    if (index >= fieldNames.length) return null;

    String[] elements;
    synchronized (this) {
      elements = values;
    }
    return elements != null ? elements[index] : null;
  }

  @Override public final void put(String name, String value) {
    int index = indexOf(name);
    if (index == -1) return;
    put(index, value);
  }

  public final void put(int index, String value) {
    if (index >= fieldNames.length) return;

    synchronized (this) {
      String[] elements = values;
      if (elements == null) {
        elements = new String[fieldNames.length];
        elements[index] = value;
      } else if (value.equals(elements[index])) {
        return;
      } else { // this is the copy-on-write part
        elements = Arrays.copyOf(elements, elements.length);
        elements[index] = value;
      }
      values = elements;
    }
  }

  @Override protected final void putAllIfAbsent(PropagationFields parent) {
    if (!(parent instanceof PredefinedPropagationFields)) return;
    PredefinedPropagationFields predefinedParent = (PredefinedPropagationFields) parent;
    checkSameFields(predefinedParent);
    String[] parentValues = predefinedParent.values;
    if (parentValues == null) return;
    for (int i = 0; i < parentValues.length; i++) {
      if (parentValues[i] != null && get(i) == null) { // extracted wins vs parent
        put(i, parentValues[i]);
      }
    }
  }

  void checkSameFields(PredefinedPropagationFields predefinedParent) {
    if (!Arrays.equals(fieldNames, predefinedParent.fieldNames)) {
      throw new IllegalStateException(
        String.format("Mixed name configuration unsupported: found %s, expected %s",
          Arrays.toString(fieldNames), Arrays.toString(predefinedParent.fieldNames))
      );
    }
  }

  @Override public final Map<String, String> toMap() {
    String[] elements;
    synchronized (this) {
      elements = values;
    }

    if (elements == null) return Collections.emptyMap();

    Map<String, String> contents = new LinkedHashMap<>();
    for (int i = 0, length = fieldNames.length; i < length; i++) {
      String maybeValue = elements[i];
      if (maybeValue == null) continue;
      contents.put(fieldNames[i], maybeValue);
    }
    return Collections.unmodifiableMap(contents);
  }

  int indexOf(String name) {
    for (int i = 0, length = fieldNames.length; i < length; i++) {
      if (fieldNames[i].equals(name)) return i;
    }
    return -1;
  }

  @Override public int hashCode() { // for unit tests
    return values == null ? 0 : Arrays.hashCode(values);
  }

  @Override public boolean equals(Object o) { // for unit tests
    if (o == this) return true;
    if (!(o instanceof PredefinedPropagationFields)) return false;
    PredefinedPropagationFields that = (PredefinedPropagationFields) o;
    return values == null ? that.values == null : Arrays.equals(values, that.values);
  }
}

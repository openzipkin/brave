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

import brave.internal.InternalPropagation;
import brave.internal.Nullable;
import brave.internal.PropagationFields;
import brave.internal.PropagationFieldsFactory;
import brave.propagation.TraceContext;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Copy-on-write keeps propagation changes in a child context from affecting its parent */
final class PredefinedBaggageFields extends PropagationFields<BaggageField, String> {
  static final class Factory
    extends PropagationFieldsFactory<BaggageField, String, PredefinedBaggageFields> {
    final BaggageField[] fields;

    Factory(BaggageField... fields) {
      this.fields = fields;
    }

    @Override public Class<PredefinedBaggageFields> type() {
      return PredefinedBaggageFields.class;
    }

    @Override public PredefinedBaggageFields create() {
      return new PredefinedBaggageFields(fields);
    }

    @Override public PredefinedBaggageFields create(PredefinedBaggageFields parent) {
      return new PredefinedBaggageFields(parent, fields);
    }

    @Override protected TraceContext contextWithExtra(TraceContext context, List<Object> extra) {
      return InternalPropagation.instance.withExtra(context, extra); // more efficient
    }
  }

  final BaggageField[] fields;
  volatile String[] values; // guarded by this, copy on write

  PredefinedBaggageFields(BaggageField... fields) {
    this.fields = fields;
  }

  PredefinedBaggageFields(PredefinedBaggageFields parent, BaggageField... fields) {
    this(fields);
    checkSameFields(parent);
    this.values = parent.values;
  }

  @Override protected String get(BaggageField field) {
    int index = indexOf(field);
    return index != -1 ? get(index) : null;
  }

  String get(int index) {
    if (index >= fields.length) return null;

    String[] elements = values;
    return elements != null ? elements[index] : null;
  }

  @Override protected void forEach(FieldConsumer<BaggageField, String> fieldConsumer) {
    String[] elements = values;
    if (elements == null) return;

    for (int i = 0, length = fields.length; i < length; i++) {
      String value = elements[i];
      if (value == null) continue;
      fieldConsumer.accept(fields[i], value);
    }
  }

  @Override protected final boolean put(BaggageField field, String value) {
    int index = indexOf(field);
    if (index == -1) return false;
    return put(index, value);
  }

  @Override protected boolean isEmpty() {
    String[] elements = values;
    if (elements == null) return true;
    for (String value : elements) {
      if (value != null) return false;
    }
    return true;
  }

  protected final boolean put(int index, @Nullable String value) {
    if (index >= fields.length) return false;

    synchronized (this) {
      return doPut(index, value);
    }
  }

  boolean doPut(int index, @Nullable String value) {
    String[] elements = values;
    if (elements == null) {
      elements = new String[fields.length];
      elements[index] = value;
    } else if (equal(value, elements[index])) {
      return false;
    } else { // this is the copy-on-write part
      elements = Arrays.copyOf(elements, elements.length);
      elements[index] = value;
    }
    values = elements;
    return true;
  }

  @Override protected final void putAllIfAbsent(PropagationFields parent) {
    if (!(parent instanceof PredefinedBaggageFields)) return;
    PredefinedBaggageFields predefinedParent = (PredefinedBaggageFields) parent;
    checkSameFields(predefinedParent);
    String[] parentValues = predefinedParent.values;
    if (parentValues == null) return;
    for (int i = 0; i < parentValues.length; i++) {
      if (parentValues[i] != null && get(i) == null) { // extracted wins vs parent
        doPut(i, parentValues[i]);
      }
    }
  }

  void checkSameFields(PredefinedBaggageFields predefinedParent) {
    if (!Arrays.equals(fields, predefinedParent.fields)) {
      throw new IllegalStateException(
        String.format("Mixed name configuration unsupported: found %s, expected %s",
          Arrays.toString(fields), Arrays.toString(predefinedParent.fields))
      );
    }
  }

  @Override public final Map<String, String> toMap() {
    String[] elements = values;
    if (elements == null) return Collections.emptyMap();

    LinkedHashMap<String, String> result = new LinkedHashMap<>();
    for (int i = 0, length = fields.length; i < length; i++) {
      String value = elements[i];
      if (value != null) result.put(fields[i].name, value);
    }
    return Collections.unmodifiableMap(result);
  }

  int indexOf(BaggageField field) {
    for (int i = 0, length = fields.length; i < length; i++) {
      if (fields[i].equals(field)) return i;
    }
    return -1;
  }

  // Implemented for equals when no baggage was extracted
  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof PredefinedBaggageFields)) return false;
    PredefinedBaggageFields that = (PredefinedBaggageFields) o;
    return Arrays.equals(fields, that.fields) && Arrays.equals(values, that.values);
  }

  @Override public int hashCode() {
    int h = 1000003;
    h ^= Arrays.hashCode(fields);
    h *= 1000003;
    h ^= Arrays.hashCode(values);
    return h;
  }
}

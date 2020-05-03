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
package brave.internal.baggage;

import brave.baggage.BaggageField;
import brave.internal.Nullable;
import brave.internal.baggage.UnsafeArrayMap.Mapper;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static brave.internal.baggage.ExtraBaggageFields.equal;

/** Handles {@link BaggageField} value storage with a string array. */
final class FixedBaggageState extends ExtraBaggageFields.State<Object[]> {
  static final Mapper<Object, String> FIELD_TO_NAME = new Mapper<Object, String>() {
    @Override public String map(Object input) {
      return ((BaggageField) input).name();
    }
  };
  static final UnsafeArrayMap.Builder<String, String> MAP_STRING_STRING_BUILDER =
      UnsafeArrayMap.<String, String>newBuilder().mapKeys(FIELD_TO_NAME);

  FixedBaggageState(FixedBaggageFieldsFactory factory, Object[] parentState) {
    super(factory, parentState);
  }

  FixedBaggageFieldsFactory factory() {
    return (FixedBaggageFieldsFactory) factory;
  }

  @Override public boolean isDynamic() {
    return false;
  }

  @Override public List<BaggageField> getAllFields() {
    return factory().initialFieldList;
  }

  @Override Map<String, String> getAllValues() {
    return MAP_STRING_STRING_BUILDER.build(state);
  }

  @Override @Nullable public String getValue(BaggageField field) {
    if (field == null) return null;
    Object[] state = this.state;
    int i = indexOfField(state, field);
    return i != -1 ? (String) state[i + 1] : null;
  }

  int indexOfField(Object[] state, BaggageField field) {
    Integer index = factory().initialFieldIndices.get(field);
    if (index != null) return index;
    for (int i = factory().initialArrayLength; i < state.length; i += 2) {
      if (state[i] == null) break; // end of keys
      if (field.equals(state[i])) return i;
    }
    return -1;
  }

  @Override public boolean updateValue(BaggageField field, @Nullable String value) {
    if (field == null) return false;

    int i = indexOfField(state, field);
    if (i == -1) return false; // not a fixed field

    synchronized (this) {
      Object[] state = this.state;
      if (equal(value, state[i + 1])) return false;

      state = Arrays.copyOf(state, state.length); // copy-on-write
      state[i + 1] = value;
      this.state = state;
      return true;
    }
  }

  @Override void mergeStateKeepingOursOnConflict(ExtraBaggageFields toMerge) {
    Object[] theirState = ((FixedBaggageState) toMerge.internal).state;

    if (state == (factory()).initialState) {
      state = theirState;
      return;
    }

    boolean dirty = false;
    for (int i = 1; i < state.length; i += 2) {
      if (state[i] != null) continue; // our state wins
      if (!dirty) {
        state = Arrays.copyOf(state, state.length); // copy-on-write
        dirty = true;
      }
      state[i] = theirState[i];
    }
  }

  // Implemented for equals when no baggage was extracted
  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof FixedBaggageState)) return false;
    return Arrays.equals(state, ((FixedBaggageState) o).state);
  }

  @Override public int hashCode() {
    return Arrays.hashCode(state);
  }
}
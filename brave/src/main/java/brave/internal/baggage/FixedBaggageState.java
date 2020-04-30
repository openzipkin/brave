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
import java.util.Arrays;
import java.util.List;

import static brave.internal.baggage.ExtraBaggageFields.equal;

/** Handles {@link BaggageField} value storage with a string array. */
final class FixedBaggageState extends ExtraBaggageFields.State<String[]> {
  final BaggageField[] fields;
  final List<BaggageField> fixedFieldList;

  FixedBaggageState(FixedBaggageFieldsFactory factory, String[] parentState) {
    super(factory, parentState);
    this.fields = factory.fields;
    this.fixedFieldList = factory.fixedFieldList;
  }

  @Override public boolean isDynamic() {
    return false;
  }

  @Override public List<BaggageField> getAllFields() {
    return fixedFieldList;
  }

  @Override @Nullable public String getValue(BaggageField field) {
    int index = indexOf(field);
    if (index == -1) return null;
    return this.state[index];
  }

  @Override public boolean updateValue(BaggageField field, @Nullable String value) {
    int index = indexOf(field);
    if (index == -1) return false;

    synchronized (this) {
      String[] state = this.state;
      if (equal(value, state[index])) return false;

      state = Arrays.copyOf(state, state.length); // copy-on-write
      state[index] = value;
      this.state = state;
      return true;
    }
  }

  @Override void mergeStateKeepingOursOnConflict(ExtraBaggageFields toMerge) {
    String[] theirState = ((FixedBaggageState) toMerge.internal).state;

    if (state == ((FixedBaggageFieldsFactory) factory).initialState) {
      state = theirState;
      return;
    }

    boolean dirty = false;
    for (int i = 0; i < state.length; i++) {
      if (state[i] != null) continue; // our state wins
      if (!dirty) {
        state = Arrays.copyOf(state, state.length); // copy-on-write
        dirty = true;
      }
      state[i] = theirState[i];
    }
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
    if (!(o instanceof FixedBaggageState)) return false;
    return Arrays.equals(state, ((FixedBaggageState) o).state);
  }

  @Override public int hashCode() {
    return Arrays.hashCode(state);
  }
}
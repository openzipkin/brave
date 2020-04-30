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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static brave.internal.baggage.ExtraBaggageFields.equal;

/** Handles {@link BaggageField} value storage with a map. */
final class DynamicBaggageState extends ExtraBaggageFields.State<Map<BaggageField, String>> {
  DynamicBaggageState(DynamicBaggageFieldsFactory factory, Map<BaggageField, String> parentState) {
    super(factory, parentState);
  }

  @Override public boolean isDynamic() {
    return true;
  }

  /** The list of fields present, regardless of value. */
  @Override public List<BaggageField> getAllFields() {
    return Collections.unmodifiableList(new ArrayList<>(state.keySet()));
  }

  @Override public String getValue(BaggageField field) {
    return state.get(field);
  }

  @Override public boolean updateValue(BaggageField field, String value) {
    synchronized (this) {
      Map<BaggageField, String> state = this.state;
      if (equal(value, state.get(field))) return false;

      // We replace an existing value with null instead of deleting it. This way we know there was
      // a field value at some point (ex for reverting state).
      LinkedHashMap<BaggageField, String> mergedState = new LinkedHashMap<>(state);
      mergedState.put(field, value);
      this.state = mergedState;
    }
    return true;
  }

  @Override void mergeStateKeepingOursOnConflict(ExtraBaggageFields toMerge) {
    Map<BaggageField, String> theirState = ((DynamicBaggageState) toMerge.internal).state;

    if (state == ((DynamicBaggageFieldsFactory) factory).initialState) {
      state = theirState;
      return;
    }

    boolean dirty = false;
    for (BaggageField field : theirState.keySet()) {
      String thisValue = state.get(field);
      if (thisValue != null) continue; // our state wins
      if (!dirty) {
        state = new LinkedHashMap<>(state); // copy-on-write
        dirty = true;
      }
      state.put(field, theirState.get(field));
    }
  }

  // Implemented for equals when no baggage was extracted
  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof DynamicBaggageState)) return false;
    return state.equals(((DynamicBaggageState) o).state);
  }

  @Override public int hashCode() {
    return state.hashCode();
  }
}
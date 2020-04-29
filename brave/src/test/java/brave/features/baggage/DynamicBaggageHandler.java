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
package brave.features.baggage;

import brave.baggage.BaggageField;
import brave.internal.Nullable;
import brave.internal.baggage.BaggageHandler;
import brave.internal.baggage.BaggageHandler.StateDecoder;
import brave.internal.baggage.BaggageHandler.StateEncoder;
import brave.propagation.TraceContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

/**
 * This accepts any fields, but only uses one remote value. This is an example, but not of good
 * performance.
 */
final class DynamicBaggageHandler implements BaggageHandler<Map<BaggageField, String>>,
    StateDecoder<Map<BaggageField, String>>, StateEncoder<Map<BaggageField, String>> {
  static final DynamicBaggageHandler INSTANCE = new DynamicBaggageHandler();

  static DynamicBaggageHandler get() {
    return INSTANCE;
  }

  DynamicBaggageHandler() {
  }

  @Override public boolean isDynamic() {
    return true;
  }

  @Override public List<BaggageField> currentFields(@Nullable Map<BaggageField, String> state) {
    if (state == null) return Collections.emptyList();
    return new ArrayList<>(state.keySet());
  }

  @Override public boolean handlesField(BaggageField field) {
    return true; // grow indefinitely
  }

  @Override public String getValue(BaggageField field, Map<BaggageField, String> state) {
    return state.get(field);
  }

  @Override
  public Map<BaggageField, String> updateState(@Nullable Map<BaggageField, String> state,
      BaggageField field, @Nullable String value) {
    if (state == null) state = Collections.emptyMap();
    if (Objects.equals(value, state.get(field))) return state;
    if (value == null) {
      if (!state.containsKey(field)) return state;
      if (state.size() == 1) return Collections.emptyMap();
    }

    // We replace an existing value with null instead of deleting it. This way we know there was
    // a field value at some point (ex for reverting state).
    LinkedHashMap<BaggageField, String> mergedState = new LinkedHashMap<>(state);
    mergedState.put(field, value);
    return mergedState;
  }

  @Override public <R> Map<BaggageField, String> decode(R request, String value) {
    Map<BaggageField, String> result = new LinkedHashMap<>();
    for (String entry : value.split(",")) {
      String[] keyValue = entry.split("=", 2);
      result.put(BaggageField.create(keyValue[0]), keyValue[1]);
    }
    return result;
  }

  @Override
  public <R> String encode(Map<BaggageField, String> state, TraceContext context, R request) {
    StringBuilder result = new StringBuilder();
    Iterator<Entry<BaggageField, String>> iterator = state.entrySet().iterator();
    while (iterator.hasNext()) {
      Entry<BaggageField, String> entry = iterator.next();
      result.append(entry.getKey().name()).append('=').append(entry.getValue());
      if (iterator.hasNext()) result.append(',');
    }
    return result.toString();
  }
}

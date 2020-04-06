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
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.logging.log4j.core.util.StringBuilderWriter;

import static com.google.common.base.Objects.equal;

/**
 * This accepts any fields, but only uses one remote value. This is an example, but not of good
 * performance.
 */
final class DynamicBaggageHandler implements BaggageHandler<Map<BaggageField, String>> {
  static BaggageHandler<Map<BaggageField, String>> create() {
    return new DynamicBaggageHandler();
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

  @Override public Map<BaggageField, String> newState(BaggageField field, String value) {
    LinkedHashMap<BaggageField, String> newState = new LinkedHashMap<>();
    newState.put(field, value);
    return newState;
  }

  @Override
  public Map<BaggageField, String> mergeState(Map<BaggageField, String> state, BaggageField field,
    String value) {
    if (equal(value, state.get(field))) return state;
    if (value == null) {
      if (!state.containsKey(field)) return state;
      if (state.size() == 1) return null;
    }

    // We replace an existing value with null instead of deleting it. This way we know there was
    // a field value at some point (ex for reverting state).
    LinkedHashMap<BaggageField, String> mergedState = new LinkedHashMap<>(state);
    mergedState.put(field, value);
    return mergedState;
  }

  @Override
  public Map<BaggageField, String> fromRemoteValue(String encoded) {
    Properties decoded = new Properties();
    try {
      decoded.load(new StringReader(encoded));
    } catch (IOException e) {
      throw new AssertionError(e);
    }

    Map<BaggageField, String> result = new LinkedHashMap<>();
    for (Map.Entry<Object, Object> entry : decoded.entrySet()) {
      result.put(BaggageField.create(entry.getKey().toString()), entry.getValue().toString());
    }
    return result;
  }

  @Override public String toRemoteValue(Map<BaggageField, String> state) {
    Properties encoded = new Properties();
    state.forEach((f, v) -> encoded.put(f.name(), v));
    StringBuilder result = new StringBuilder();
    try {
      encoded.store(new StringBuilderWriter(result), "");
    } catch (IOException e) {
      throw new AssertionError(e);
    }
    return result.toString();
  }
}

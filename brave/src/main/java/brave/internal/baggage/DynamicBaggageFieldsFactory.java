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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DynamicBaggageFieldsFactory extends ExtraBaggageFieldsFactory {
  /** @param fields possibly empty initial fields. */
  public static ExtraBaggageFieldsFactory create(List<BaggageField> fields) {
    if (fields == null) throw new NullPointerException("fields == null");
    Map<BaggageField, String> initialState;
    if (fields.isEmpty()) {
      initialState = Collections.emptyMap();
    } else { // seed any field names so that they return in getAllFields()
      initialState = new LinkedHashMap<>();
      for (BaggageField field : fields) initialState.put(field, null);
    }
    return new DynamicBaggageFieldsFactory(Collections.unmodifiableMap(initialState));
  }

  final Map<BaggageField, String> initialState;

  DynamicBaggageFieldsFactory(Map<BaggageField, String> initialState) {
    this.initialState = initialState;
  }

  @Override public ExtraBaggageFields create() {
    return new ExtraBaggageFields(new DynamicBaggageState(this, initialState));
  }

  @Override public ExtraBaggageFields create(ExtraBaggageFields parent) {
    Map<BaggageField, String> parentState =
        parent != null ? ((DynamicBaggageState) parent.internal).state : initialState;
    return new ExtraBaggageFields(new DynamicBaggageState(this, parentState));
  }
}

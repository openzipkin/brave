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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BaggageFieldsHandler extends ExtraHandler<BaggageFields, BaggageFieldsHandler> {
  public static final int MAX_DYNAMIC_FIELDS = Long.SIZE;

  public static BaggageFieldsHandler create(List<BaggageField> fields, boolean dynamic) {
    if (fields == null) throw new NullPointerException("fields == null");
    Map<BaggageField, Integer> initialFieldIndices = new LinkedHashMap<>();
    Object[] array = new Object[fields.size() * 2];
    int i = 0;
    for (BaggageField field : fields) {
      initialFieldIndices.put(field, i);
      array[i] = field;
      i += 2;
    }
    return new BaggageFieldsHandler(array, initialFieldIndices, dynamic);
  }

  final Map<BaggageField, Integer> initialFieldIndices;
  final List<BaggageField> initialFieldList;
  final boolean isDynamic;
  final int initialArrayLength;

  BaggageFieldsHandler(
      Object[] initialState, Map<BaggageField, Integer> initialFieldIndices, boolean isDynamic) {
    super(initialState);
    this.initialFieldIndices = Collections.unmodifiableMap(initialFieldIndices);
    this.initialFieldList =
        Collections.unmodifiableList(new ArrayList<>(initialFieldIndices.keySet()));
    this.isDynamic = isDynamic;
    this.initialArrayLength = initialState.length;
  }

  @Override protected BaggageFields newExtra(@Nullable Object request) {
    return new BaggageFields(this);
  }
}

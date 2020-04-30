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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class FixedBaggageFieldsFactory extends ExtraBaggageFieldsFactory {
  public static ExtraBaggageFieldsFactory create(List<BaggageField> fields) {
    if (fields == null) throw new NullPointerException("fields == null");
    if (fields.isEmpty()) throw new NullPointerException("fields are empty");
    return new FixedBaggageFieldsFactory(fields.toArray(new BaggageField[0]));
  }

  final BaggageField[] fields;
  final String[] initialState;
  final List<BaggageField> fixedFieldList;

  public FixedBaggageFieldsFactory(BaggageField[] fields) {
    this.fields = fields;
    this.initialState = new String[fields.length];
    this.fixedFieldList = Collections.unmodifiableList(Arrays.asList(fields));
  }

  @Override public ExtraBaggageFields create() {
    return new ExtraBaggageFields(new FixedBaggageState(this, initialState));
  }

  @Override public ExtraBaggageFields create(ExtraBaggageFields parent) {
    String[] parentState =
        parent != null ? ((FixedBaggageState) parent.internal).state : initialState;
    return new ExtraBaggageFields(new FixedBaggageState(this, parentState));
  }
}

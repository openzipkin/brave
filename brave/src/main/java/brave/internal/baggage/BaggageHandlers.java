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
import java.util.List;

public final class BaggageHandlers {
  /** Only handles a single non-remote field. */
  public static BaggageHandler<String> string(BaggageField field) {
    if (field == null) throw new NullPointerException("field == null");
    return new StringBaggageHandler(field);
  }

  static final class StringBaggageHandler implements BaggageHandler<String> {
    final BaggageField field;
    final List<BaggageField> fieldList;

    StringBaggageHandler(BaggageField field) {
      this.field = field;
      this.fieldList = Collections.singletonList(field);
    }

    @Override public boolean isDynamic() {
      return false;
    }

    @Override public List<BaggageField> currentFields(String state) {
      return fieldList;
    }

    @Override public boolean handlesField(BaggageField field) {
      return this.field.equals(field);
    }

    @Override public String getValue(BaggageField field, String state) {
      return state;
    }

    @Override public String updateState(String state, BaggageField field, String value) {
      return value; // overwrite
    }

    /** Returns true for any config with the same baggage field. */
    @Override public boolean equals(Object o) {
      if (o == this) return true;
      if (!(o instanceof StringBaggageHandler)) return false;
      return field.equals(((StringBaggageHandler) o).field);
    }

    /** Returns the same value for any config with the same baggage field. */
    @Override public int hashCode() {
      return field.hashCode();
    }

    @Override public String toString() {
      return field.toString();
    }
  }
}

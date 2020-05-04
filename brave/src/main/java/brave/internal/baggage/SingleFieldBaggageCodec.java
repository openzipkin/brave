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
import brave.propagation.TraceContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class SingleFieldBaggageCodec implements BaggageCodec {
  /** Only handles a single remote field. */
  public static SingleFieldBaggageCodec single(BaggageField field, Collection<String> keyNames) {
    if (field == null) throw new NullPointerException("field == null");
    return new SingleFieldBaggageCodec(field, keyNames);
  }

  final BaggageField field;
  final List<String> keyNamesList;

  SingleFieldBaggageCodec(BaggageField field, Collection<String> keyNames) {
    this.field = field;
    this.keyNamesList = Collections.unmodifiableList(new ArrayList<>(keyNames));
  }

  @Override public List<String> extractKeyNames() {
    return keyNamesList;
  }

  @Override public List<String> injectKeyNames() {
    return keyNamesList;
  }

  @Override
  public boolean decode(BaggageField.ValueUpdater valueUpdater, Object request, String value) {
    return valueUpdater.updateValue(field, value);
  }

  @Override public String encode(Map<String, String> values, TraceContext context, Object request) {
    return field.getValue(context);
  }
}

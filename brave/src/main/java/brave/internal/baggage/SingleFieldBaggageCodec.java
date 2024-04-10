/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
    this.keyNamesList = Collections.unmodifiableList(new ArrayList<String>(keyNames));
  }

  @Override public List<String> extractKeyNames() {
    return keyNamesList;
  }

  @Override public List<String> injectKeyNames() {
    return keyNamesList;
  }

  @Override public boolean decode(BaggageField.ValueUpdater valueUpdater, String value) {
    return valueUpdater.updateValue(field, value);
  }

  @Override public String encode(Map<String, String> values, TraceContext context) {
    return field.getValue(context);
  }
}

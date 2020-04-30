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
import brave.baggage.BaggagePropagationConfig;
import brave.internal.baggage.BaggageCodec;
import brave.internal.baggage.ExtraBaggageFields;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * This is a non-complete codec for the w3c (soon to be renamed to "baggage") header.
 *
 * <p>See https://github.com/w3c/correlation-context/blob/master/correlation_context/HTTP_HEADER_FORMAT.md
 */
final class SingleHeaderCodec implements BaggageCodec {
  public static Builder newBuilder() {
    return new Builder();
  }

  public static class Builder { // not final to backport ExtraFieldPropagation
    String keyName = "baggage";
    final Set<BaggageField> blacklist = new LinkedHashSet<>();

    /** Overrides the {@link Propagation#keys() key name}. Defaults to "baggage". */
    public Builder keyName(String keyName) {
      if (keyName == null) throw new NullPointerException("keyName == null");
      this.keyName = keyName;
      return this;
    }

    /**
     * Exclude a specific field from this format. By default, all fields will be serialized, even
     * those made with {@link BaggagePropagationConfig.SingleBaggageField#remote(BaggageField)}.
     */
    Builder blacklistField(BaggageField field) {
      blacklist.add(field);
      return this;
    }

    /** Returns the keyName if there are no fields to propagate. */
    public BaggageCodec build() {
      return new SingleHeaderCodec(this);
    }
  }

  final List<String> keyNames;
  final Set<BaggageField> blacklist;

  SingleHeaderCodec(Builder builder) {
    keyNames = Collections.singletonList(builder.keyName);
    blacklist = new LinkedHashSet<>(builder.blacklist);
  }

  @Override public List<String> extractKeyNames() {
    return keyNames;
  }

  @Override public List<String> injectKeyNames() {
    return keyNames;
  }

  @Override public boolean decode(ExtraBaggageFields extra, Object request, String value) {
    assert extra.isDynamic() : "This is unlikely to work with fixed fields!";
    boolean decoded = false;
    for (String entry : value.split(",")) {
      String[] keyValue = entry.split("=", 2);
      if (extra.updateValue(BaggageField.create(keyValue[0]), keyValue[1])) decoded = true;
    }
    return decoded;
  }

  @Override public String encode(ExtraBaggageFields extra, TraceContext context, Object request) {
    StringBuilder result = new StringBuilder();
    for (BaggageField field : extra.getAllFields()) {
      if (blacklist.contains(field)) continue;
      String value = extra.getValue(field);
      if (value != null) {
        if (result.length() > 0) result.append(',');
        result.append(field.name()).append('=').append(value);
      }
    }
    return result.length() == 0 ? null : result.toString();
  }
}

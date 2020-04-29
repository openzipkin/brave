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
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.Collections;
import java.util.List;

/**
 * Most commonly, field storage is inside {@link TraceContext#extra()}.
 *
 * @see ExtraBaggageFields
 */
public final class ExtraBaggageContext extends BaggageContext {
  static final ExtraBaggageContext INSTANCE = new ExtraBaggageContext();

  public static BaggageContext get() {
    return INSTANCE;
  }

  public static List<BaggageField> getAllFields(TraceContextOrSamplingFlags extracted) {
    if (extracted.context() != null) return getAllFields(extracted.context());
    return getAllFields(extracted.extra());
  }

  public static List<BaggageField> getAllFields(TraceContext context) {
    return getAllFields(context.extra());
  }

  @Nullable
  public static BaggageField getFieldByName(TraceContextOrSamplingFlags extracted, String name) {
    if (extracted.context() != null) return getFieldByName(extracted.context(), name);
    return getFieldByName(getAllFields(extracted.extra()), name);
  }

  @Nullable public static BaggageField getFieldByName(TraceContext context, String name) {
    return getFieldByName(getAllFields(context.extra()), name);
  }

  @Override public String getValue(BaggageField field, TraceContextOrSamplingFlags extracted) {
    if (extracted.context() != null) return getValue(field, extracted.context());
    return getValue(field, extracted.extra());
  }

  @Override public String getValue(BaggageField field, TraceContext context) {
    return getValue(field, context.extra());
  }

  @Override public boolean updateValue(BaggageField field, TraceContextOrSamplingFlags extracted,
    @Nullable String value) {
    if (extracted.context() != null) return updateValue(field, extracted.context(), value);
    return updateValue(field, extracted.extra(), value);
  }

  @Override public boolean updateValue(BaggageField field, TraceContext context, String value) {
    return updateValue(field, context.extra(), value);
  }

  static List<BaggageField> getAllFields(List<Object> extra) {
    ExtraBaggageFields fields = findExtra(ExtraBaggageFields.class, extra);
    if (fields == null) return Collections.emptyList();
    return fields.getAllFields();
  }

  @Nullable static BaggageField getFieldByName(List<BaggageField> fields, String name) {
    if (name == null) throw new NullPointerException("name == null");
    name = name.trim();
    if (name.isEmpty()) throw new IllegalArgumentException("name is empty");
    for (BaggageField field : fields) {
      if (name.equals(field.name())) {
        return field;
      }
    }
    return null;
  }

  @Nullable static String getValue(BaggageField field, List<Object> extra) {
    ExtraBaggageFields fields = findExtra(ExtraBaggageFields.class, extra);
    if (fields == null) return null;
    return fields.getValue(field);
  }

  static boolean updateValue(BaggageField field, List<Object> extra, @Nullable String value) {
    ExtraBaggageFields fields = findExtra(ExtraBaggageFields.class, extra);
    return fields != null && fields.updateValue(field, value);
  }

  public static <T> T findExtra(Class<T> type, List<Object> extra) {
    if (type == null) throw new NullPointerException("type == null");
    for (int i = 0, length = extra.size(); i < length; i++) {
      Object nextExtra = extra.get(i);
      if (nextExtra.getClass() == type) return (T) nextExtra;
    }
    return null;
  }
}

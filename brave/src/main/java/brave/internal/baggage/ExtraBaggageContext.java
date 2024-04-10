/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.internal.baggage;

import brave.baggage.BaggageField;
import brave.internal.Nullable;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Most commonly, field storage is inside {@link TraceContext#extra()}.
 *
 * @see BaggageFields
 */
public final class ExtraBaggageContext extends BaggageContext {
  static final ExtraBaggageContext INSTANCE = new ExtraBaggageContext();

  public static BaggageContext get() {
    return INSTANCE;
  }

  public static Map<String, String> getAllValues(TraceContextOrSamplingFlags extracted) {
    if (extracted.context() != null) return getAllValues(extracted.context());
    return getAllValues(extracted.extra());
  }

  public static Map<String, String> getAllValues(TraceContext context) {
    return getAllValues(context.extra());
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

  static List<BaggageField> getAllFields(List<Object> extraList) {
    BaggageFields extra = findExtra(BaggageFields.class, extraList);
    if (extra == null) return Collections.emptyList();
    return extra.getAllFields();
  }

  static Map<String, String> getAllValues(List<Object> extraList) {
    BaggageFields extra = findExtra(BaggageFields.class, extraList);
    if (extra == null) return Collections.emptyMap();
    return extra.getAllValues();
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

  @Nullable static String getValue(BaggageField field, List<Object> extraList) {
    BaggageFields extra = findExtra(BaggageFields.class, extraList);
    if (extra == null) return null;
    return extra.getValue(field);
  }

  static boolean updateValue(BaggageField field, List<Object> extraList, @Nullable String value) {
    BaggageFields extra = findExtra(BaggageFields.class, extraList);
    return extra != null && extra.updateValue(field, value);
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

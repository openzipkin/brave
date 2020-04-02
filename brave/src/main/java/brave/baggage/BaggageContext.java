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
package brave.baggage;

import brave.internal.Nullable;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.Collections;
import java.util.List;

import static brave.baggage.BaggageField.validateName;
import static java.util.Arrays.asList;

/** Internal type that implements context storage for the field. */
abstract class BaggageContext {
  static final BaggageContext EXTRA = new Extra();

  @Nullable abstract String getValue(BaggageField field, TraceContextOrSamplingFlags extracted);

  @Nullable abstract String getValue(BaggageField field, TraceContext context);

  /** Returns false if the update was ignored. */
  abstract boolean updateValue(BaggageField field, TraceContextOrSamplingFlags extracted,
    @Nullable String value);

  /** Returns false if the update was ignored. */
  abstract boolean updateValue(BaggageField field, TraceContext context, @Nullable String value);

  /** Appropriate for constants or immutable fields defined in {@link TraceContext}. */
  static abstract class ReadOnly extends BaggageContext {
    @Override boolean updateValue(BaggageField field, TraceContextOrSamplingFlags extracted,
      @Nullable String value) {
      return false;
    }

    @Override boolean updateValue(BaggageField field, TraceContext context, String value) {
      return false;
    }
  }

  /** Most commonly, field storage is inside {@link TraceContext#extra()} */
  static final class Extra extends BaggageContext {
    static List<BaggageField> getAll(TraceContextOrSamplingFlags extracted) {
      if (extracted.context() != null) return getAll(extracted.context());
      return getAll(extracted.extra());
    }

    static List<BaggageField> getAll(TraceContext context) {
      return getAll(context.extra());
    }

    static @Nullable BaggageField getByName(TraceContextOrSamplingFlags extracted, String name) {
      if (extracted.context() != null) return getByName(extracted.context(), name);
      return getByName(extracted.extra(), name);
    }

    static @Nullable BaggageField getByName(TraceContext context, String name) {
      return getByName(context.extra(), name);
    }

    @Override String getValue(BaggageField field, TraceContextOrSamplingFlags extracted) {
      if (extracted.context() != null) return getValue(field, extracted.context());
      return getValue(field, extracted.extra());
    }

    @Override String getValue(BaggageField field, TraceContext context) {
      return getValue(field, context.extra());
    }

    @Override boolean updateValue(BaggageField field, TraceContextOrSamplingFlags extracted,
      @Nullable String value) {
      if (extracted.context() != null) return updateValue(field, extracted.context(), value);
      return updateValue(field, extracted.extra(), value);
    }

    @Override boolean updateValue(BaggageField field, TraceContext context, String value) {
      return updateValue(field, context.extra(), value);
    }

    static List<BaggageField> getAll(List<Object> extra) {
      PredefinedBaggageFields fields = findExtra(PredefinedBaggageFields.class, extra);
      if (fields == null) return Collections.emptyList();
      return Collections.unmodifiableList(asList(fields.fields));
    }

    @Nullable static BaggageField getByName(List<Object> extra, String name) {
      name = validateName(name);
      PredefinedBaggageFields fields = findExtra(PredefinedBaggageFields.class, extra);
      if (fields == null) return null;
      for (BaggageField field : fields.fields) {
        if (name.equals(field.name())) {
          return field;
        }
      }
      return null;
    }

    @Nullable static String getValue(BaggageField field, List<Object> extra) {
      PredefinedBaggageFields fields = findExtra(PredefinedBaggageFields.class, extra);
      if (fields == null) return null;
      return fields.get(field);
    }

    static boolean updateValue(BaggageField field, List<Object> extra, @Nullable String value) {
      PredefinedBaggageFields fields = findExtra(PredefinedBaggageFields.class, extra);
      return fields != null && fields.put(field, value);
    }
  }

  static <T> T findExtra(Class<T> type, List<Object> extra) {
    if (type == null) throw new NullPointerException("type == null");
    for (int i = 0, length = extra.size(); i < length; i++) {
      Object nextExtra = extra.get(i);
      if (nextExtra.getClass() == type) return (T) nextExtra;
    }
    return null;
  }
}

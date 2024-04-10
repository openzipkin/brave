/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.internal.baggage;

import brave.baggage.BaggageField;
import brave.internal.Nullable;
import brave.internal.collect.UnsafeArrayMap;
import brave.internal.collect.UnsafeArrayMap.Mapper;
import brave.internal.extra.MapExtra;
import brave.internal.extra.MapExtraFactory;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Holds one or more baggage fields in {@link TraceContext#extra()} or {@link
 * TraceContextOrSamplingFlags#extra()}.
 */
public final class BaggageFields
    extends MapExtra<BaggageField, String, BaggageFields, BaggageFields.Factory>
    implements BaggageField.ValueUpdater {

  static final Mapper<Object, String> FIELD_TO_NAME = new Mapper<Object, String>() {
    @Override public String map(Object input) {
      return ((BaggageField) input).name();
    }
  };
  static final UnsafeArrayMap.Builder<String, String> MAP_STRING_STRING_BUILDER =
      UnsafeArrayMap.<String, String>newBuilder().mapKeys(FIELD_TO_NAME);

  public static Factory newFactory(List<BaggageField> fields, int maxDynamicEntries) {
    if (fields == null) throw new NullPointerException("fields == null");
    FactoryBuilder builder = new FactoryBuilder();
    for (BaggageField field : fields) builder.addInitialKey(field);
    return builder.maxDynamicEntries(maxDynamicEntries).build();
  }

  static final class FactoryBuilder extends
      MapExtraFactory.Builder<BaggageField, String, BaggageFields, Factory, FactoryBuilder> {
    @Override protected Factory build() {
      return new Factory(this);
    }
  }

  public static final class Factory
      extends MapExtraFactory<BaggageField, String, BaggageFields, Factory> {
    Factory(FactoryBuilder builder) {
      super(builder);
    }

    @Override public BaggageFields create() {
      return new BaggageFields(this);
    }
  }

  BaggageFields(Factory factory) {
    super(factory);
  }

  Object[] state() {
    return (Object[]) state;
  }

  @Override public boolean updateValue(BaggageField field, String value) {
    return put(field, value);
  }

  @Nullable public String getValue(BaggageField key) {
    return super.get(key);
  }

  /**
   * The list of fields present, regardless of value. The result is cacheable unless {@link
   * #isDynamic()}.
   */
  public List<BaggageField> getAllFields() {
    return Collections.unmodifiableList(new ArrayList<BaggageField>(keySet()));
  }

  /** Returns a read-only view of the non-null baggage field values */
  public Map<String, String> toMapFilteringFieldNames(String... filtered) {
    return UnsafeArrayMap.<String, String>newBuilder().mapKeys(FIELD_TO_NAME)
        .filterKeys(filtered)
        .build(state());
  }

  /** Returns a possibly empty map of all name to non-{@code null} values. */
  public Map<String, String> getAllValues() {
    return MAP_STRING_STRING_BUILDER.build(state());
  }
}

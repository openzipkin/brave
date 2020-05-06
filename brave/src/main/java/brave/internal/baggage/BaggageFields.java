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
import brave.internal.Platform;
import brave.internal.collect.LongBitSet;
import brave.internal.collect.UnsafeArrayMap;
import brave.internal.collect.UnsafeArrayMap.Mapper;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static brave.internal.baggage.BaggageFieldsHandler.MAX_DYNAMIC_FIELDS;
import static brave.internal.collect.LongBitSet.isSet;
import static brave.internal.collect.LongBitSet.setBit;

/**
 * Holds one or more baggage fields in {@link TraceContext#extra()} or {@link
 * TraceContextOrSamplingFlags#extra()}.
 */
public final class BaggageFields extends Extra<BaggageFields, BaggageFieldsFactory>
    implements BaggageField.ValueUpdater {
  static final Mapper<Object, String> FIELD_TO_NAME = new Mapper<Object, String>() {
    @Override public String map(Object input) {
      return ((BaggageField) input).name();
    }
  };
  static final UnsafeArrayMap.Builder<String, String> MAP_STRING_STRING_BUILDER =
      UnsafeArrayMap.<String, String>newBuilder().mapKeys(FIELD_TO_NAME);

  BaggageFields(BaggageFieldsFactory factory) {
    super(factory);
  }

  Object[] state() {
    return (Object[]) state;
  }

  /** When true, calls to {@link #getAllFields()} cannot be cached. */
  public boolean isDynamic() {
    return factory.isDynamic;
  }

  /** The list of fields present, regardless of value. */
  public List<BaggageField> getAllFields() {
    if (!factory.isDynamic) return factory.initialFieldList;
    Object[] state = state();
    List<BaggageField> result = new ArrayList<>(state.length / 2);
    for (int i = 0; i < state.length; i += 2) {
      result.add((BaggageField) state[i]);
    }
    return Collections.unmodifiableList(result);
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

  /**
   * Returns the value of the field with the specified name or {@code null} if not available.
   *
   * @see BaggageField#getValue(TraceContext)
   * @see BaggageField#getValue(TraceContextOrSamplingFlags)
   */
  @Nullable public String getValue(BaggageField field) {
    if (field == null) return null;
    Object[] state = state();
    int i = indexOfExistingField(state, field);
    return i != -1 ? (String) state[i + 1] : null;
  }

  @Override public boolean updateValue(BaggageField field, @Nullable String value) {
    if (field == null) return false;

    int i = indexOfExistingField(state(), field);
    if (i == -1 && !factory.isDynamic) {
      Platform.get().log("Ignoring request to add a dynamic field", null);
      return false;
    }

    synchronized (lock) {
      Object[] prior = state();

      // double-check lost race in dynamic case
      if (i == -1) i = indexOfDynamicField(prior, field);
      if (i == -1) return addNewField(prior, field, value);

      if (equal(value, prior[i + 1])) return false;

      Object[] newState = Arrays.copyOf(prior, prior.length); // copy-on-write
      newState[i + 1] = value;
      this.state = newState;
      return true;
    }
  }

  @Override protected void mergeStateKeepingOursOnConflict(BaggageFields theirFields) {
    Object[] ourstate = state(), theirstate = theirFields.state();

    // scan first to see if we need to grow our state.
    long newToOurs = 0;
    for (int i = 0; i < theirstate.length; i += 2) {
      if (theirstate[i] == null) break; // end of keys
      int ourIndex = indexOfExistingField(ourstate, (BaggageField) theirstate[i]);
      if (ourIndex == -1) newToOurs = setBit(newToOurs, i / 2);
    }

    boolean growthAllowed = true;
    int newstateLength = ourstate.length + LongBitSet.size(newToOurs) * 2;
    if (newstateLength > ourstate.length) {
      if (!factory.isDynamic) {
        Platform.get().log("Ignoring request to add a dynamic field", null);
        growthAllowed = false;
      } else if (newstateLength / 2 > MAX_DYNAMIC_FIELDS) {
        Platform.get().log("Ignoring request to add > %s dynamic fields", MAX_DYNAMIC_FIELDS, null);
        growthAllowed = false;
      }
    }

    // To implement copy-on-write, we provision a new state large enough for all changes.
    Object[] newState = null;

    // Now, we iterate through all changes and apply them
    int endOfOurs = ourstate.length;
    for (int i = 0; i < theirstate.length; i += 2) {
      if (theirstate[i] == null) break; // end of keys
      Object theirValue = theirstate[i + 1];

      // Check if the current index is a new field
      if (isSet(newToOurs, i / 2)) {
        if (!growthAllowed) continue;

        if (newState == null) newState = Arrays.copyOf(ourstate, newstateLength);
        newState[endOfOurs] = theirstate[i];
        newState[endOfOurs + 1] = theirValue;
        endOfOurs += 2;
        continue;
      }

      // Now, check if this field exists in our state, potentially with the same value.
      int ourIndex = indexOfExistingField(ourstate, (BaggageField) theirstate[i]);
      assert ourIndex != -1;

      // Ensure we don't mutate the state when our value should win
      Object ourValue = ourstate[ourIndex + 1];
      if (ourValue != null || theirValue == null) continue;

      // At this point, we have a change to an existing field, apply it.
      if (newState == null) newState = Arrays.copyOf(ourstate, newstateLength);
      newState[ourIndex + 1] = theirValue;
    }
    if (newState != null) state = newState;
  }

  int indexOfExistingField(Object[] state, BaggageField field) {
    int i = indexOfInitialField(field);
    if (i == -1 && factory.isDynamic) {
      i = indexOfDynamicField(state, field);
    }
    return i;
  }

  /**
   * Fields are never deleted, only their valuse set {@code null}. This means existing indexes are
   * stable for instances of this type.
   */
  int indexOfInitialField(BaggageField field) {
    Integer index = factory.initialFieldIndices.get(field);
    return index != null ? index : -1;
  }

  int indexOfDynamicField(Object[] state, BaggageField field) {
    for (int i = factory.initialArrayLength; i < state.length; i += 2) {
      if (state[i] == null) break; // end of keys
      if (field.equals(state[i])) return i;
    }
    return -1;
  }

  /** Grows the state to append a new field/value pair unless we reached a limit. */
  boolean addNewField(Object[] prior, BaggageField field, @Nullable String value) {
    int newIndex = prior.length;
    int newstateLength = newIndex + 2;
    if (newstateLength / 2 > MAX_DYNAMIC_FIELDS) {
      Platform.get().log("Ignoring request to add > %s dynamic fields", MAX_DYNAMIC_FIELDS, null);
      return false;
    }
    Object[] newState = Arrays.copyOf(prior, newstateLength); // copy-on-write
    newState[newIndex] = field;
    newState[newIndex + 1] = value;
    this.state = newState;
    return true;
  }

  @Override protected boolean stateEquals(Object thatState) {
    return Arrays.equals(state(), (Object[]) thatState);
  }

  @Override protected int stateHashCode() {
    return Arrays.hashCode(state());
  }

  @Override protected String stateString() {
    return Arrays.toString(state());
  }
}

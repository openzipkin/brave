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
package brave.internal.extra;

import brave.internal.Nullable;
import brave.internal.Platform;
import brave.internal.collect.LongBitSet;
import brave.internal.collect.UnsafeArrayMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static brave.internal.collect.LongBitSet.isSet;
import static brave.internal.collect.LongBitSet.setBit;
import static brave.internal.extra.MapExtraFactory.MAX_DYNAMIC_ENTRIES;

public class MapExtra<K, V, A extends MapExtra<K, V, A, F>,
    F extends MapExtraFactory<K, V, A, F>> extends Extra<A, F> {
  protected MapExtra(F factory) {
    super(factory);
  }

  Object[] state() {
    return (Object[]) state;
  }

  /** When true, calls to {@link #asReadOnlyMap()}, {@link Map#keySet()} cannot be cached. */
  protected boolean isDynamic() {
    return factory.maxDynamicEntries > 0;
  }

  /** Returns {@code true} when all values are {@code null}. */
  protected boolean isEmpty() {
    Object[] state = state();
    for (int i = 0; i < state.length; i += 2) {
      if (state[i + 1] != null) return false;
    }
    return true;
  }

  /** Returns a possibly empty set of all keys even if values are {@code null}. */
  protected Set<K> keySet() {
    if (!isDynamic()) return factory.initialFieldIndices.keySet();
    Object[] state = state();
    Set<K> result = new LinkedHashSet<>(state.length / 2);
    for (int i = 0; i < state.length; i += 2) {
      result.add((K) state[i]);
    }
    return Collections.unmodifiableSet(result);
  }

  /** Returns a possibly empty map of all key to non-{@code null} values. */
  protected Map<K, V> asReadOnlyMap() {
    return UnsafeArrayMap.<K, V>newBuilder().build(state());
  }

  /** Returns the value of the {@code key} or {@code null} if not available. */
  @Nullable protected V get(K key) {
    if (key == null) return null;
    Object[] state = state();
    int i = indexOfExistingKey(state, key);
    return i != -1 ? (V) state[i + 1] : null;
  }

  /**
   * Updates the value of the {@code key}, or ignores if read-only or not configured.
   *
   * @param value {@code null} is an attempt to remove the value
   * @return {@code true} if the underlying state changed
   * @since 5.12
   */
  protected boolean put(K key, @Nullable V value) {
    if (key == null) return false;

    int i = indexOfExistingKey(state(), key);
    if (i == -1 && factory.maxDynamicEntries == 0) {
      Platform.get().log("Ignoring request to add a dynamic key", null);
      return false;
    }

    synchronized (lock) {
      Object[] prior = state();

      // double-check lost race in dynamic case
      if (i == -1) i = indexOfDynamicKey(prior, key);
      if (i == -1) return addNewEntry(prior, key, value);

      if (equal(value, prior[i + 1])) return false;

      Object[] newState = Arrays.copyOf(prior, prior.length); // copy-on-write
      newState[i + 1] = value;
      this.state = newState;
      return true;
    }
  }

  @Override protected void mergeStateKeepingOursOnConflict(A theirFields) {
    Object[] ourstate = state(), theirstate = theirFields.state();

    // scan first to see if we need to grow our state.
    long newToOurs = 0;
    for (int i = 0; i < theirstate.length; i += 2) {
      if (theirstate[i] == null) break; // end of keys
      int ourIndex = indexOfExistingKey(ourstate, (K) theirstate[i]);
      if (ourIndex == -1) newToOurs = setBit(newToOurs, i / 2);
    }

    boolean growthAllowed = true;
    int newstateLength = ourstate.length + LongBitSet.size(newToOurs) * 2;
    if (newstateLength > ourstate.length) {
      if (newstateLength / 2 > factory.maxDynamicEntries) {
        Platform.get().log("Ignoring request to add > %s dynamic keys", MAX_DYNAMIC_ENTRIES, null);
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

      // Check if the current index is a new key
      if (isSet(newToOurs, i / 2)) {
        if (!growthAllowed) continue;

        if (newState == null) newState = Arrays.copyOf(ourstate, newstateLength);
        newState[endOfOurs] = theirstate[i];
        newState[endOfOurs + 1] = theirValue;
        endOfOurs += 2;
        continue;
      }

      // Now, check if this key exists in our state, potentially with the same value.
      int ourIndex = indexOfExistingKey(ourstate, (K) theirstate[i]);
      assert ourIndex != -1;

      // Ensure we don't mutate the state when our value should win
      Object ourValue = ourstate[ourIndex + 1];
      if (ourValue != null || theirValue == null) continue;

      // At this point, we have a change to an existing key, apply it.
      if (newState == null) newState = Arrays.copyOf(ourstate, newstateLength);
      newState[ourIndex + 1] = theirValue;
    }
    if (newState != null) state = newState;
  }

  int indexOfExistingKey(Object[] state, K key) {
    int i = indexOfInitialKey(key);
    if (i == -1 && factory.maxDynamicEntries > 0) {
      i = indexOfDynamicKey(state, key);
    }
    return i;
  }

  /**
   * Keys are never deleted, only their valuse set {@code null}. This means existing indexes are
   * stable for instances of this type.
   */
  int indexOfInitialKey(K key) {
    Integer index = factory.initialFieldIndices.get(key);
    return index != null ? index : -1;
  }

  int indexOfDynamicKey(Object[] state, K key) {
    for (int i = factory.initialArrayLength; i < state.length; i += 2) {
      if (state[i] == null) break; // end of keys
      if (key.equals(state[i])) return i;
    }
    return -1;
  }

  /** Grows the state to append a new key/value pair unless we reached a limit. */
  boolean addNewEntry(Object[] prior, K key, @Nullable V value) {
    int newIndex = prior.length;
    int newstateLength = newIndex + 2;
    if (newstateLength / 2 > MAX_DYNAMIC_ENTRIES) {
      Platform.get().log("Ignoring request to add > %s dynamic entries", MAX_DYNAMIC_ENTRIES, null);
      return false;
    }
    Object[] newState = Arrays.copyOf(prior, newstateLength); // copy-on-write
    newState[newIndex] = key;
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

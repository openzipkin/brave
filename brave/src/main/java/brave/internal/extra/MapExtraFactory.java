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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static brave.internal.collect.LongBitSet.MAX_SIZE;

public abstract class MapExtraFactory<K, V, A extends MapExtra<K, V, A, F>,
    F extends MapExtraFactory<K, V, A, F>> extends ExtraFactory<A, F> {
  public static final int MAX_DYNAMIC_ENTRIES = MAX_SIZE;

  // no static newBuilder method as we don't want to have shadowing in the concrete subtype

  public static abstract class Builder<K, V, A extends MapExtra<K, V, A, F>,
      F extends MapExtraFactory<K, V, A, F>, B extends Builder<K, V, A, F, B>> {
    List<Object> initialState = new ArrayList<>();
    int maxDynamicEntries;

    public final B addInitialKey(K key) {
      if (key == null) throw new NullPointerException("key == null");
      initialState.add(key);
      initialState.add(null);
      return (B) this;
    }

    public final B maxDynamicEntries(int maxDynamicEntries) {
      if (maxDynamicEntries < 0) throw new IllegalArgumentException("maxDynamicEntries < 0");
      if (maxDynamicEntries > MAX_SIZE) {
        throw new IllegalArgumentException("maxDynamicEntries > " + MAX_SIZE);
      }
      this.maxDynamicEntries = maxDynamicEntries;
      return (B) this;
    }

    protected abstract F build();
  }

  final Map<K, Integer> initialFieldIndices;
  final int initialArrayLength, maxDynamicEntries;

  protected MapExtraFactory(Builder<K, V, A, F, ?> builder) {
    super(builder.initialState.toArray());
    Map<K, Integer> initialFieldIndices = new LinkedHashMap<>();
    Object[] initialStateArray = (Object[]) initialState;
    this.initialArrayLength = initialStateArray.length;
    for (int i = 0; i < initialArrayLength; i += 2) {
      initialFieldIndices.put((K) initialStateArray[i], i);
    }
    this.initialFieldIndices = Collections.unmodifiableMap(initialFieldIndices);
    this.maxDynamicEntries = builder.maxDynamicEntries;
  }

  @Override protected abstract A create();
}

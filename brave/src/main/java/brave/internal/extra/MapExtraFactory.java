/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
    List<Object> initialState = new ArrayList<Object>();
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
    Map<K, Integer> initialFieldIndices = new LinkedHashMap<K, Integer>();
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

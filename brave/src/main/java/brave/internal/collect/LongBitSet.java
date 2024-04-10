/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.internal.collect;

import java.util.BitSet;

/**
 * This is more efficient than {@link BitSet} as this doesn't implicitly allocate arrays.
 */
public final class LongBitSet {
  public static final int MAX_SIZE = Long.SIZE;

  public static int size(long bitset) {
    return Long.bitCount(bitset);
  }

  public static boolean isSet(long bitset, long i) {
    return (bitset & (1 << i)) != 0;
  }

  public static long setBit(long bitset, long i) {
    return bitset | (1 << i);
  }
}

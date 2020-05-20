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

  public static long unsetBit(long bitset, long i) {
    return bitset & ~(1 << i);
  }

  public static long setBit(long bitset, long i) {
    return bitset | (1 << i);
  }
}

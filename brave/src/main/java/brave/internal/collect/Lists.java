/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.internal.collect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class Lists {

  public static <E> List<E> ensureMutable(List<E> list) {
    if (list instanceof ArrayList) return list;
    int size = list.size();
    ArrayList<E> mutable = new ArrayList<E>(size);
    for (int i = 0; i < size; i++) {
      mutable.add(list.get(i));
    }
    return mutable;
  }

  public static <E> List<E> ensureImmutable(List<E> list) {
    if (list.isEmpty()) return Collections.emptyList();
    // Faster to make a copy than check the type to see if it is already a singleton list
    if (list.size() == 1) return Collections.singletonList(list.get(0));
    if (isImmutable(list)) return list;

    return Collections.unmodifiableList(new ArrayList<E>(list));
  }

  static boolean isImmutable(List<?> extra) {
    assert extra.size() > 1;  // Handled by caller.
    // avoid copying data structure by trusting certain names.
    String simpleName = extra.getClass().getSimpleName();
    // We don't need to check EMPTY_LIST or SingletonList here since our only caller handles them
    // without type-checking.
    return simpleName.startsWith("Unmodifiable")
      || simpleName.contains("Immutable");
  }

  public static <E> List<E> concat(List<E> left, List<E> right) {
    int leftSize = left.size();
    if (leftSize == 0) return right;
    int rightSize = right.size();
    if (rightSize == 0) return left;

    // We have to concat. Use Arrays.asList instead of ArrayList as it is simpler due to fixed size
    E[] array = (E[]) new Object[leftSize + rightSize];
    int i = 0;
    for (int l = 0; l < leftSize; l++) {
      array[i++] = left.get(l);
    }
    for (int r = 0; r < rightSize; r++) {
      array[i++] = right.get(r);
    }
    return Collections.unmodifiableList(Arrays.asList(array));
  }

  Lists() {
  }
}

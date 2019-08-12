/*
 * Copyright 2013-2019 The OpenZipkin Authors
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
package brave.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Lists {

  public static List<Object> ensureMutable(List<Object> list) {
    if (list instanceof ArrayList) return list;
    int size = list.size();
    ArrayList<Object> mutable = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      mutable.add(list.get(i));
    }
    return mutable;
  }

  public static List<Object> ensureImmutable(List<Object> extra) {
    if (isImmutable(extra)) return extra;
    // Faster to make a copy than check the type to see if it is already a singleton list
    if (extra.size() == 1) return Collections.singletonList(extra.get(0));
    return Collections.unmodifiableList(new ArrayList<>(extra));
  }

  static boolean isImmutable(List<Object> extra) {
    if (extra == Collections.EMPTY_LIST) return true;
    // avoid copying datastructure by trusting certain names.
    String simpleName = extra.getClass().getSimpleName();
    return simpleName.equals("SingletonList")
      || simpleName.startsWith("Unmodifiable")
      || simpleName.contains("Immutable");
  }

  public static List<Object> concatImmutableLists(List<Object> left, List<Object> right) {
    int leftSize = left.size();
    if (leftSize == 0) return right;
    int rightSize = right.size();
    if (rightSize == 0) return left;

    // now we know we have to concat
    ArrayList<Object> mutable = new ArrayList<>();
    for (int i = 0; i < leftSize; i++) {
      mutable.add(left.get(i));
    }
    for (int i = 0; i < rightSize; i++) {
      mutable.add(right.get(i));
    }
    return Collections.unmodifiableList(mutable);
  }

  Lists() {
  }
}

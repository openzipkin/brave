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

import java.lang.reflect.Array;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import static brave.internal.baggage.LongBitSet.isSet;
import static brave.internal.baggage.LongBitSet.setBit;

/**
 * A potentially read-only map which is a view over an array of {@code key, value} pairs. No key can
 * be {@code null}. Keys with {@code null} are filtered out.
 *
 * <p>The array is shared with the caller to {@link Builder#build(Object[])}, hence being called
 * "unsafe". This type supports cheap views over data using thread-local or copy-on-write arrays.
 *
 * <p>An input with no keys coerces to {@link Collections#emptyMap()}.
 *
 * <p>As this is an immutable view, operations like {@link #keySet()}, {@link #values()} and {@link
 * #entrySet()} might return constants. As expected, stateful objects such as {@link Iterator} will
 * never be shared.
 */
class UnsafeArrayMap<K, V> implements Map<K, V> {
  static final int MAX_FILTERED_KEYS = LongBitSet.MAX_SIZE;

  interface Mapper<V1, V2> {
    V2 map(V1 input);
  }

  static <K, V> Builder<K, V> newBuilder() {
    return new Builder<>();
  }

  static final class Builder<K, V> {
    Mapper<Object, K> keyMapper;
    K[] filteredKeys = (K[]) new Object[0];

    Builder<K, V> mapKeys(Mapper<Object, K> keyMapper) {
      if (keyMapper == null) throw new NullPointerException("keyMapper == null");
      this.keyMapper = keyMapper;
      return this;
    }

    /** @param filteredKeys keys that won't be visible in the resulting map. */
    Builder<K, V> filterKeys(K... filteredKeys) {
      if (filteredKeys == null) throw new NullPointerException("filteredKeys == null");
      if (filteredKeys.length > MAX_FILTERED_KEYS) {
        throw new IllegalArgumentException(
            "cannot filter more than " + MAX_FILTERED_KEYS + " keys");
      }
      this.filteredKeys = filteredKeys;
      return this;
    }

    /** @param array pairwise array holding key values */
    Map<K, V> build(Object[] array) {
      if (array == null) throw new NullPointerException("array == null");
      long filteredBitSet = 0;
      int i = 0, numFiltered = 0;
      for (; i < array.length; i += 2) {
        if (array[i] == null) break; // we ignore anything starting at first null key

        if (array[i + 1] == null) { // filter null values
          filteredBitSet = setFilteredKey(filteredBitSet, i);
          numFiltered++;
          continue;
        }

        K key = keyMapper != null ? keyMapper.map(array[i]) : (K) array[i];
        for (K filteredKey : filteredKeys) {
          if (filteredKey.equals(key)) {
            filteredBitSet = setFilteredKey(filteredBitSet, i);
            numFiltered++;
            break;
          }
        }
      }
      if (numFiltered == i / 2) return Collections.emptyMap();
      if (keyMapper == null) return new UnsafeArrayMap<>(array, i, filteredBitSet);
      return new KeyMapping<>(this, array, i, filteredBitSet);
    }
  }

  static final class KeyMapping<K, V> extends UnsafeArrayMap<K, V> {
    final Mapper<Object, K> keyMapper;

    KeyMapping(Builder builder, Object[] array, int toIndex, long filteredBitSet) {
      super(array, toIndex, filteredBitSet);
      this.keyMapper = builder.keyMapper;
    }

    @Override K key(int i) {
      return keyMapper.map(array[i]);
    }
  }

  final Object[] array;
  final int toIndex, size;
  final long filteredBitSet;

  UnsafeArrayMap(Object[] array, int toIndex, long filteredBitSet) {
    this.array = array;
    this.toIndex = toIndex;
    this.filteredBitSet = filteredBitSet;
    this.size = toIndex / 2 - LongBitSet.size(filteredBitSet);
  }

  @Override public int size() {
    return size;
  }

  @Override public boolean containsKey(Object o) {
    if (o == null) return false; // null keys are not allowed
    return arrayIndexOfKey(o) != -1;
  }

  @Override public boolean containsValue(Object o) {
    for (int i = 0; i < toIndex; i += 2) {
      if (!isFilteredKey(filteredBitSet, i) && value(i + 1).equals(o)) return true;
    }
    return false;
  }

  @Override public V get(Object o) {
    if (o == null) return null; // null keys are not allowed
    int i = arrayIndexOfKey(o);
    return i != -1 ? value(i + 1) : null;
  }

  int arrayIndexOfKey(Object o) {
    int result = -1;
    for (int i = 0; i < toIndex; i += 2) {
      if (!isFilteredKey(filteredBitSet, i) && o.equals(key(i))) {
        return i;
      }
    }
    return result;
  }

  K key(int i) {
    return (K) array[i];
  }

  V value(int i) {
    return (V) array[i];
  }

  @Override public Set<K> keySet() {
    return new KeySetView();
  }

  @Override public Collection<V> values() {
    return new ValuesView();
  }

  @Override public Set<Map.Entry<K, V>> entrySet() {
    return new EntrySetView();
  }

  @Override public boolean isEmpty() {
    return false;
  }

  @Override public V put(K key, V value) {
    throw new UnsupportedOperationException();
  }

  @Override public V remove(Object key) {
    throw new UnsupportedOperationException();
  }

  @Override public void putAll(Map<? extends K, ? extends V> m) {
    throw new UnsupportedOperationException();
  }

  @Override public void clear() {
    throw new UnsupportedOperationException();
  }

  final class KeySetView extends SetView<K> {
    @Override K elementAtArrayIndex(int i) {
      return key(i);
    }

    @Override public boolean contains(Object o) {
      return containsKey(o);
    }
  }

  final class ValuesView extends SetView<V> {
    @Override V elementAtArrayIndex(int i) {
      return value(i + 1);
    }

    @Override public boolean contains(Object o) {
      return containsValue(o);
    }
  }

  final class EntrySetView extends SetView<Map.Entry<K, V>> {
    @Override Map.Entry<K, V> elementAtArrayIndex(int i) {
      return new SimpleImmutableEntry<>(key(i), value(i + 1));
    }

    @Override public boolean contains(Object o) {
      if (!(o instanceof Map.Entry) || ((Map.Entry) o).getKey() == null) return false;
      Map.Entry that = (Map.Entry) o;
      int i = arrayIndexOfKey(that.getKey());
      if (i == -1) return false;
      return value(i + 1).equals(that.getValue());
    }
  }

  abstract class SetView<E> implements Set<E> {
    int advancePastFiltered(int i) {
      while (i < toIndex && isFilteredKey(filteredBitSet, i)) i += 2;
      return i;
    }

    @Override public int size() {
      return size;
    }

    /**
     * By abstracting this, {@link #keySet()} {@link #values()} and {@link #entrySet()} only
     * implement need implement two methods based on {@link #<E>}: this method and and {@link
     * #contains(Object)}.
     */
    abstract E elementAtArrayIndex(int i);

    @Override public Iterator<E> iterator() {
      return new ReadOnlyIterator();
    }

    @Override public Object[] toArray() {
      return copyTo(new Object[size]);
    }

    @Override public <T> T[] toArray(T[] a) {
      T[] result = a.length >= size ? a
          : (T[]) Array.newInstance(a.getClass().getComponentType(), size());
      return copyTo(result);
    }

    <T> T[] copyTo(T[] dest) {
      for (int i = 0, d = 0; i < toIndex; i += 2) {
        if (isFilteredKey(filteredBitSet, i)) continue;
        dest[d++] = (T) elementAtArrayIndex(i);
      }
      return dest;
    }

    final class ReadOnlyIterator implements Iterator<E> {
      int i = advancePastFiltered(0);

      @Override public boolean hasNext() {
        i = advancePastFiltered(i);
        return i < toIndex;
      }

      @Override public E next() {
        if (!hasNext()) throw new NoSuchElementException();
        E result = elementAtArrayIndex(i);
        i += 2;
        return result;
      }

      @Override public void remove() {
        throw new UnsupportedOperationException();
      }
    }

    @Override public boolean containsAll(Collection<?> c) {
      if (c == null) return false;
      if (c.isEmpty()) return true;

      for (Object element : c) {
        if (!contains(element)) return false;
      }
      return true;
    }

    @Override public boolean isEmpty() {
      return false;
    }

    @Override public boolean add(E e) {
      throw new UnsupportedOperationException();
    }

    @Override public boolean remove(Object o) {
      throw new UnsupportedOperationException();
    }

    @Override public boolean addAll(Collection<? extends E> c) {
      throw new UnsupportedOperationException();
    }

    @Override public boolean retainAll(Collection<?> c) {
      throw new UnsupportedOperationException();
    }

    @Override public boolean removeAll(Collection<?> c) {
      throw new UnsupportedOperationException();
    }

    @Override public void clear() {
      throw new UnsupportedOperationException();
    }
  }

  @Override public String toString() {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < toIndex; i += 2) {
      if (isFilteredKey(filteredBitSet, i)) continue;
      if (result.length() > 0) result.append(',');
      result.append(key(i)).append('=').append(value(i + 1));
    }
    return result.insert(0, "UnsafeArrayMap{").append("}").toString();
  }

  static long setFilteredKey(long filteredKeys, int i) {
    return setBit(filteredKeys, i / 2);
  }

  static boolean isFilteredKey(long filteredKeys, int i) {
    return isSet(filteredKeys, i / 2);
  }
}

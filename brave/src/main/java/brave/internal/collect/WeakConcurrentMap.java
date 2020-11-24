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

import brave.internal.Nullable;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.AbstractMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * This borrows heavily from Rafael Winterhalter's {@code com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap}
 * with the following major changes:
 *
 * <p>The biggest change is this removes LatentKey. Instead it relies on keys known to have a
 * stable {@link #hashCode} and who are {@linkplain #equals(Object) equal to} a weak reference of
 * itself. We allow lookups using externally created contexts, yet don't want to incur overhead of
 * key allocation or classloader problems sharing keys with a thread local.
 *
 * <p>Other changes mostly remove features (to reduce the bytecode size) and address style:
 * <ul>
 *   <li>Inline expunction only as we have no thread to use anyway</li>
 *   <li>Stylistic changes including different javadoc and removal of private modifiers</li>
 *   <li>toString: derived only from keys</li>
 * </ul>
 *
 * <p>See https://github.com/raphw/weak-lock-free
 */
public class WeakConcurrentMap<K, V> extends ReferenceQueue<K> implements Iterable<Map.Entry<K, V>> {
  final ConcurrentMap<WeakKey<K>, V> target = new ConcurrentHashMap<>();

  @Nullable public V getIfPresent(K key) {
    if (key == null) throw new NullPointerException("key == null");
    expungeStaleEntries();

    return target.get(key);
  }

  /** Replaces the entry with the indicated key and returns the old value or {@code null}. */
  @Nullable public V putIfProbablyAbsent(K key, V value) {
    if (key == null) throw new NullPointerException("key == null");
    if (value == null) throw new NullPointerException("value == null");
    expungeStaleEntries();

    return target.putIfAbsent(new WeakKey<>(key, this), value);
  }

  /** Removes the entry with the indicated key and returns the old value or {@code null}. */
  @Nullable public V remove(K key) {
    if (key == null) throw new NullPointerException("key == null");
    expungeStaleEntries();

    return target.remove(key);
  }

  /** Iterates over the entries in this map. */
  @Override
  public Iterator<Map.Entry<K, V>> iterator() {
    return new EntryIterator(target.entrySet().iterator());
  }

  /** Cleans all unused references. */
  protected void expungeStaleEntries() {
    Reference<?> reference;
    while ((reference = poll()) != null) {
      removeStaleEntry(reference);
    }
  }

  protected V removeStaleEntry(Reference<?> reference) {
    return target.remove(reference);
  }

  // This comment was directly verbatim from https://github.com/raphw/weak-lock-free/blob/dcbd2fa0d30571bb3ed187a42cb75323a5569d5b/src/main/java/com/blogspot/mydailyjava/weaklockfree/WeakConcurrentMap.java#L273-L302
  /*
   * Why this works:
   * ---------------
   *
   * Note that this map only supports reference equality for keys and uses system hash codes. Also, for the
   * WeakKey instances to function correctly, we are voluntarily breaking the Java API contract for
   * hashCode/equals of these instances.
   *
   *
   * System hash codes are immutable and can therefore be computed prematurely and are stored explicitly
   * within the WeakKey instances. This way, we always know the correct hash code of a key and always
   * end up in the correct bucket of our target map. This remains true even after the weakly referenced
   * key is collected.
   *
   * If we are looking up the value of the current key via WeakConcurrentMap::get or any other public
   * API method, we know that any value associated with this key must still be in the map as the mere
   * existence of this key makes it ineligible for garbage collection. Therefore, looking up a value
   * using another WeakKey wrapper guarantees a correct result.
   *
   * If we are looking up the map entry of a WeakKey after polling it from the reference queue, we know
   * that the actual key was already collected and calling WeakKey::get returns null for both the polled
   * instance and the instance within the map. Since we explicitly stored the identity hash code for the
   * referenced value, it is however trivial to identify the correct bucket. From this bucket, the first
   * weak key with a null reference is removed. Due to hash collision, we do not know if this entry
   * represents the weak key. However, we do know that the reference queue polls at least as many weak
   * keys as there are stale map entries within the target map. If no key is ever removed from the map
   * explicitly, the reference queue eventually polls exactly as many weak keys as there are stale entries.
   *
   * Therefore, we can guarantee that there is no memory leak.
   */
  static final class WeakKey<T> extends WeakReference<T> {
    final int hashCode;

    WeakKey(T key, ReferenceQueue<? super T> queue) {
      super(key, queue);
      this.hashCode = key.hashCode(); // cache as hashCode is used for all future operations
    }

    @Override public int hashCode() {
      return hashCode;
    }

    @Override public String toString() {
      T value = get();
      return value != null ? value.toString() : "ClearedReference()";
    }

    /**
     * While a lookup key will invoke equals against this, the visa versa is not true. This method
     * is only used inside the target map to resolve hash code collisions.
     */
    @Override public boolean equals(Object o) { // resolves hash code collisions
      if (o == this) return true;
      assert o instanceof WeakReference : "Bug: unexpected input to equals";
      return equal(get(), ((WeakReference) o).get());
    }
  }

  @Override public String toString() {
    Class<?> thisClass = getClass();
    while (thisClass.getSimpleName().isEmpty()) {
      thisClass = thisClass.getSuperclass();
    }
    expungeStaleEntries(); // Clean up so that only present references show up (unless race lost)
    return thisClass.getSimpleName() + target.keySet();
  }

  static boolean equal(@Nullable Object a, @Nullable Object b) {
    return a == null ? b == null : a.equals(b); // Java 6 can't use Objects.equals()
  }

  class EntryIterator implements Iterator<Map.Entry<K, V>> {

    private final Iterator<Map.Entry<WeakKey<K>, V>> iterator;

    private Map.Entry<WeakKey<K>, V> nextEntry;

    private K nextKey;

    private EntryIterator(Iterator<Map.Entry<WeakKey<K>, V>> iterator) {
      this.iterator = iterator;
      findNext();
    }

    private void findNext() {
      while (iterator.hasNext()) {
        nextEntry = iterator.next();
        nextKey = nextEntry.getKey().get();
        if (nextKey != null) {
          return;
        }
      }
      nextEntry = null;
      nextKey = null;
    }

    @Override
    public boolean hasNext() {
      return nextKey != null;
    }

    @Override
    public Map.Entry<K, V> next() {
      if (nextKey == null) {
        throw new NoSuchElementException();
      }
      try {
        return new AbstractMap.SimpleImmutableEntry<K, V>(nextKey, nextEntry.getValue());
      } finally {
        findNext();
      }
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
}

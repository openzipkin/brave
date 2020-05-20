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

import brave.GarbageCollectors;
import brave.internal.collect.WeakConcurrentMap.WeakKey;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class WeakConcurrentMapTest {

  static class TestKey {
    final String key;

    TestKey(String key) {
      this.key = key;
    }

    @Override public int hashCode() {
      return key.hashCode();
    }

    @Override public boolean equals(Object o) {
      if (o == this) return true;
      // Hack that allows WeakConcurrentMap to lookup without allocating a new object.
      if (o instanceof WeakReference) o = ((WeakReference) o).get();
      if (!(o instanceof TestKey)) return false;
      return key.equals(((TestKey) o).key);
    }

    @Override public String toString() {
      return key;
    }
  }

  WeakConcurrentMap<TestKey, Object> map = new WeakConcurrentMap<>();
  TestKey key = new TestKey("a");

  @Test public void getOrCreate_whenSomeReferencesAreCleared() {
    map.putIfProbablyAbsent(key, "1");
    pretendGCHappened();
    map.putIfProbablyAbsent(key, "1");

    // we'd expect two distinct entries..
    assertThat(map.target.keySet())
      .extracting(WeakReference::get)
      .containsExactlyInAnyOrder(null, key);
  }

  @Test public void remove_clearsReference() {
    map.putIfProbablyAbsent(key, "1");
    map.remove(key);

    assertThat(map.target).isEmpty();
    assertThat(map.poll()).isNull();
  }

  @Test
  public void remove_okWhenDoesntExist() {
    map.remove(key);
  }

  @Test
  public void remove_resolvesHashCodeCollisions() {
    // intentionally clash on hashCode, but not equals
    TestKey key1 = new TestKey("a") {
      @Override public int hashCode() {
        return 1;
      }
    };
    TestKey key2 = new TestKey("b") {
      @Override public int hashCode() {
        return 1;
      }
    };

    // sanity check
    assertThat(key1.hashCode()).isEqualTo(key2.hashCode());
    assertThat(key1).isNotEqualTo(key2);

    map.putIfProbablyAbsent(key1, "1");
    assertThat(map.putIfProbablyAbsent(key2, "2")).isNull();

    map.remove(key1);

    assertThat(map.target.keySet()).extracting(o -> ((Reference) o).get())
      .containsOnly(key2);
  }

  /** mainly ensures internals aren't dodgy on null */
  @Test public void remove_whenSomeReferencesAreCleared() {
    map.putIfProbablyAbsent(key, "1");
    pretendGCHappened();
    map.remove(key);

    assertThat(map.target.keySet()).extracting(WeakReference::get)
      .hasSize(1)
      .containsNull();
  }

  @Test public void weakKey_equalToItself() {
    WeakKey<TestKey> key = new WeakKey<>(new TestKey("a"), map);
    assertThat(key).isEqualTo(key);
    key.clear();
    assertThat(key).isEqualTo(key);
  }

  @Test public void weakKey_equalToEquivalent() {
    WeakKey<TestKey> key = new WeakKey<>(new TestKey("a"), map);
    WeakKey<TestKey> key2 = new WeakKey<>(new TestKey("a"), map);
    assertThat(key).isEqualTo(key2);
    key.clear();
    assertThat(key).isNotEqualTo(key2);
    key2.clear();
    assertThat(key).isEqualTo(key2);
  }

  /** Debugging should show what the spans are, as well any references pending clear. */
  @Test public void toString_saysWhatReferentsAre() {
    assertThat(map.toString())
      .isEqualTo("WeakConcurrentMap[]");

    map.putIfProbablyAbsent(key, "1");

    assertThat(map.toString())
      .isEqualTo("WeakConcurrentMap[" + key.key + "]");

    pretendGCHappened();

    assertThat(map.toString())
      .isEqualTo("WeakConcurrentMap[ClearedReference()]");
  }

  /**
   * This is a customized version of https://github.com/raphw/weak-lock-free/blob/master/src/test/java/com/blogspot/mydailyjava/weaklockfree/WeakConcurrentMapTest.java
   */
  @Test
  public void expungeStaleEntries_afterGC() {
    TestKey key1 = new TestKey("a");
    Object value1 = new Object();
    map.putIfProbablyAbsent(key1, value1);
    TestKey key2 = new TestKey("b");
    Object value2 = new Object();
    map.putIfProbablyAbsent(key2, value2);
    TestKey key3 = new TestKey("c");
    Object value3 = new Object();
    map.putIfProbablyAbsent(key3, value3);
    TestKey key4 = new TestKey("d");
    Object value4 = new Object();
    map.putIfProbablyAbsent(key4, value4);
    TestKey key5 = new TestKey("e");
    Object value5 = new Object();
    map.putIfProbablyAbsent(key5, value5);

    // By clearing strong references in this test, we are left with the weak ones in the map
    key1 = key2 = key5 = null;
    GarbageCollectors.blockOnGC();

    // After GC, we expect that the weak references of key1 and key2 to be cleared
    assertThat(map.target.keySet()).extracting(WeakReference::get)
      .usingFieldByFieldElementComparator()
      .containsExactlyInAnyOrder(null, null, key3, key4, null);

    map.expungeStaleEntries();

    // After reporting, we expect no the weak references of null
    assertThat(map.target.keySet()).extracting(WeakReference::get)
      .containsExactlyInAnyOrder(key3, key4);
  }

  /** In reality, this clears a reference even if it is strongly held by the test! */
  void pretendGCHappened() {
    map.target.keySet().iterator().next().clear();
  }
}

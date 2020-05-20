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

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.junit.Test;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

public class UnsafeArrayMapTest {
  Object[] array = new Object[6];
  UnsafeArrayMap.Builder<String, String> builder = UnsafeArrayMap.newBuilder();

  @Test public void empty() {
    Map<String, String> map = builder.build(array);
    assertThat(map).isSameAs(Collections.emptyMap());
  }

  @Test public void noNullValues() {
    array[0] = "1";
    array[1] = "one";
    array[2] = "2";
    array[3] = "two";
    array[4] = "3";
    array[5] = "three";

    Map<String, String> map = builder.build(array);
    assertSize(map, 3);
    assertBaseCase(map);

    assertThat(map).containsOnly(
        entry("1", "one"),
        entry("2", "two"),
        entry("3", "three")
    );
    assertThat(map).hasToString(
        "UnsafeArrayMap{1=one,2=two,3=three}"
    );

    assertThat(map.get("1")).isEqualTo("one");
    assertThat(map.get("2")).isEqualTo("two");
    assertThat(map.get("3")).isEqualTo("three");
  }

  @Test public void equalValues() {
    array[0] = "1";
    array[1] = "1";
    array[2] = "2";
    array[3] = "2";
    array[4] = "3";
    array[5] = "3";

    Map<String, String> map = builder.build(array);
    assertSize(map, 3);
    assertBaseCase(map);

    assertThat(map).containsOnly(
        entry("1", "1"),
        entry("2", "2"),
        entry("3", "3")
    );
    assertThat(map).hasToString(
        "UnsafeArrayMap{1=1,2=2,3=3}"
    );

    assertThat(map.get("1")).isEqualTo("1");
    assertThat(map.get("2")).isEqualTo("2");
    assertThat(map.get("3")).isEqualTo("3");
  }

  @Test public void mapKeys() {
    array[0] = " 1";
    array[1] = "one";
    array[2] = "2 ";
    array[3] = "two";
    array[4] = " 3";
    array[5] = "three";

    Map<String, String> map = builder.mapKeys(o -> ((String) o).trim()).build(array);
    assertSize(map, 3);
    assertBaseCase(map);

    assertThat(map).containsOnly(
        entry("1", "one"),
        entry("2", "two"),
        entry("3", "three")
    );
    assertThat(map).hasToString(
        "UnsafeArrayMap{1=one,2=two,3=three}"
    );

    assertThat(map.get("1")).isEqualTo("one");
    assertThat(map.get("2")).isEqualTo("two");
    assertThat(map.get("3")).isEqualTo("three");
  }

  @Test public void someNullValues() {
    array[0] = "1";
    array[1] = "one";
    array[2] = "2";
    array[3] = "two";
    array[4] = "3";

    Map<String, String> map = builder.build(array);
    assertSize(map, 2);
    assertBaseCase(map);

    assertThat(map).containsOnly(
        entry("1", "one"),
        entry("2", "two")
    );
    assertThat(map).hasToString(
        "UnsafeArrayMap{1=one,2=two}"
    );

    assertThat(map.get("1")).isEqualTo("one");
    assertThat(map.get("2")).isEqualTo("two");
    assertThat(map.get("3")).isNull();
  }

  @Test public void onlyNullValues() {
    array[0] = "1";
    array[2] = "2";
    array[4] = "3";

    Map<String, String> map = builder.build(array);
    assertThat(map).isSameAs(Collections.emptyMap());
  }

  @Test public void allFiltered() {
    array[0] = "1";
    array[1] = "one";
    array[2] = "2";
    array[3] = "two";
    array[4] = "3";
    array[5] = "three";

    Map<String, String> map = builder.filterKeys("1", "2", "3").build(array);
    assertThat(map).isSameAs(Collections.emptyMap());
  }

  @Test public void filter_tooMany() {
    String[] tooManyKeys = new String[65];

    assertThatThrownBy(() -> builder.filterKeys(tooManyKeys))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cannot filter more than 64 keys");
  }

  @Test public void someFiltered() {
    array[0] = "1";
    array[1] = "one";
    array[2] = "2";
    array[3] = "two";
    array[4] = "3";
    array[5] = "three";

    Map<String, String> map = builder.filterKeys("1", "3").build(array);
    assertSize(map, 1);
    assertBaseCase(map);

    assertThat(map).containsOnly(
        entry("2", "two")
    );
    assertThat(map).hasToString(
        "UnsafeArrayMap{2=two}"
    );

    assertThat(map.get("1")).isNull();
    assertThat(map.get("2")).isEqualTo("two");
    assertThat(map.get("3")).isNull();
  }

  @Test public void toArray() {
    array[0] = "1";
    array[1] = "one";
    array[2] = "2";
    array[3] = "two";
    array[4] = "3";
    Map<String, String> map = builder.build(array);

    testToArray(map.keySet(), "1", "2");
    testToArray(map.values(), "one", "two");
    testToArray(map.entrySet(),
        entry("1", "one"),
        entry("2", "two")
    );
  }

  <E> void testToArray(Collection<E> input, E... expected) {
    Object[] tooShort = new Object[0], exact = new Object[input.size()], tooLong = new Object[5];

    assertThat(input.toArray())
        .isNotSameAs(array)
        .containsExactly(expected);

    assertThat(input.toArray(tooShort))
        .isNotSameAs(tooShort)
        .containsExactly(expected);

    assertThat(input.toArray(exact))
        .isSameAs(exact)
        .containsExactly(expected);

    assertThat(input.toArray(tooLong))
        .isSameAs(tooLong)
        .startsWith(expected);
  }

  /**
   * Since there's only one collection impl in {@link UnsafeArrayMap}, this is less work than it
   * seems.
   */
  @Test public void unsupported() {
    array[0] = "1";
    array[1] = "1";

    Map<String, String> map = builder.build(array);

    assertThatThrownBy(() -> map.put("1", "1"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> map.putAll(map))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> map.remove("1"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(map::clear)
        .isInstanceOf(UnsupportedOperationException.class);

    Set<String> set = map.keySet();
    assertThatThrownBy(() -> set.add("2"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> set.addAll(set))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> set.retainAll(set))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> set.remove("1"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> set.removeAll(set))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(set::clear)
        .isInstanceOf(UnsupportedOperationException.class);

    assertThatThrownBy(() -> set.iterator().remove())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> map.entrySet().iterator().next().setValue("1"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  void assertSize(Map<String, String> map, int size) {
    assertThat(map).hasSize(size);
    assertThat(map.keySet()).hasSize(size);
    assertThat(map.values()).hasSize(size);
    assertThat(map.entrySet()).hasSize(size);
    assertThat(map.isEmpty()).isEqualTo(false);
    assertThat(map.keySet().isEmpty()).isEqualTo(false);
    assertThat(map.values().isEmpty()).isEqualTo(false);
    assertThat(map.entrySet().isEmpty()).isEqualTo(false);
    assertThat(map.keySet().iterator().hasNext()).isEqualTo(true);
    assertThat(map.values().iterator().hasNext()).isEqualTo(true);
    assertThat(map.entrySet().iterator().hasNext()).isEqualTo(true);
  }

  void assertBaseCase(Map<String, String> map) {
    assertThat(map)
        .doesNotContainKey(null)
        .doesNotContainEntry(null, null)
        .doesNotContainKey("4")
        .doesNotContainEntry("4", null);

    assertThat(map.keySet())
        .doesNotContainNull();

    for (String key : map.keySet()) {
      assertThat(map.containsKey(key)).isTrue();
      String value = map.get(key);
      assertThat(map.values().contains(value)).isTrue();
      assertThat(map.entrySet().contains(entry(key, value))).isTrue();
    }

    assertThat(map.entrySet())
        .extracting(Entry::getKey)
        .doesNotContainNull();

    assertThat(map.containsKey(null)).isFalse();
    assertThat(map.containsKey("4")).isFalse();

    assertThat(map.get(null)).isNull();
    assertThat(map.get("4")).isNull();

    assertThat(map.keySet().contains(null)).isFalse();

    for (Collection collection : asList(map.keySet(), map.values(), map.entrySet())) {
      assertThat(collection.containsAll(asList())).isTrue();
      assertThat(collection.containsAll(collection)).isTrue();

      assertThat(collection.contains("4")).isFalse();
      assertThat(collection.containsAll(asList("4"))).isFalse();
    }

    // only entrySet() special-cases non-object on contains
    assertThat(map.entrySet().contains(entry("4", null))).isFalse();
  }
}

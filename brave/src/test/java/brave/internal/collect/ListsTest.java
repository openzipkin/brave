/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.internal.collect;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ListsTest {

  @Test void ensureMutable_leavesArrayList() {
    List<Object> list = new ArrayList<>();

    assertThat(Lists.ensureMutable(list))
      .isSameAs(list);
  }

  @Test void ensureMutable_copiesImmutable() {
    List<Object> list = Collections.unmodifiableList(Arrays.asList("foo", "bar"));

    assertThat(Lists.ensureMutable(list))
      .isInstanceOf(ArrayList.class)
      .containsExactlyElementsOf(list);
  }

  @Test void ensureImmutable_returnsImmutableEmptyList() {
    assertThrows(UnsupportedOperationException.class, () -> {
      Lists.ensureImmutable(new ArrayList<>()).add("foo");
    });
  }

  @Test void ensureImmutable_convertsToSingletonList() {
    List<Object> list = new ArrayList<>();
    list.add("foo");
    assertThat(Lists.ensureImmutable(list).getClass().getSimpleName())
      .isEqualTo("SingletonList");
  }

  @Test void ensureImmutable_convertsToUnmodifiableList() {
    List<Long> list = new ArrayList<>();
    list.add(1L);
    list.add(2L);

    assertThat(Lists.ensureImmutable(list).getClass().getSimpleName())
      .startsWith("Unmodifiable");
  }

  @Test void ensureImmutable_returnsEmptyList() {
    List<Object> list = Collections.emptyList();
    assertThat(Lists.ensureImmutable(list))
      .isSameAs(list);
  }

  @Test void ensureImmutable_returnsEmptyListForMutableInput() {
    List<Object> list = new ArrayList<>();
    assertThat(Lists.ensureImmutable(list)).isSameAs(Collections.emptyList());
  }

  @Test void ensureImmutable_singletonListStaysSingleton() {
    List<Object> list = Collections.singletonList("foo");
    assertThat(Lists.ensureImmutable(list).getClass().getSimpleName())
      .isEqualTo("SingletonList");
  }

  @Test void ensureImmutable_doesntCopyUnmodifiableList() {
    List<Object> list = Collections.unmodifiableList(Arrays.asList("foo", "bar"));
    assertThat(Lists.ensureImmutable(list))
      .isSameAs(list);
  }

  @Test void ensureImmutable_doesntCopyImmutableList() {
    List<Object> list = ImmutableList.of("foo", "bar");
    assertThat(Lists.ensureImmutable(list))
      .isSameAs(list);
  }

  @Test void concat_choosesNonEmpty() {
    List<Object> list = ImmutableList.of("foo");
    assertThat(Lists.concat(list, Collections.emptyList()))
      .isSameAs(list);
    assertThat(Lists.concat(Collections.emptyList(), list))
      .isSameAs(list);
  }

  @Test void concat_concatenates() {
    List<Object> list1 = ImmutableList.of("foo");
    List<Object> list2 = ImmutableList.of("bar", "baz");

    assertThat(Lists.concat(list1, list2))
      .hasSameClassAs(Collections.unmodifiableList(list1))
      .containsExactly("foo", "bar", "baz");
  }
}

/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.internal.extra;

import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MapExtraTest {
  BasicMapExtra.Factory factory = new BasicMapExtra.FactoryBuilder()
      .addInitialKey("1")
      .addInitialKey("2")
      .build();
  BasicMapExtra extra = factory.create(), extra2 = factory.create();

  @Test void put() {
    extra.put("1", "one");
    assertThat(extra.get("1")).isEqualTo("one");
    assertThat(extra.isEmpty()).isFalse();
  }

  @Test void put_multiple() {
    extra.put("1", "one");
    extra.put("2", "two");
    assertThat(extra.get("1")).isEqualTo("one");
    assertThat(extra.get("2")).isEqualTo("two");

    extra.put("1", null);
    assertThat(extra.get("1")).isNull();
    assertThat(extra.get("2")).isEqualTo("two");
    assertThat(extra.isEmpty()).isFalse();

    extra.put("2", null);
    assertThat(extra.get("1")).isNull();
    assertThat(extra.get("2")).isNull();
    assertThat(extra.isEmpty()).isTrue();
  }

  @Test void put_null_clearsState() {
    extra.put("1", "one");
    extra.put("1", null);
    assertThat(extra.isEmpty()).isTrue();
  }

  @Test void empty() {
    assertThat(extra.isEmpty()).isTrue();
    extra.put("1", "one");

    assertThat(extra.isEmpty()).isFalse();

    extra.put("1", null);
    assertThat(extra.isEmpty()).isTrue();
  }

  @Test void putNoop() {
    extra.put("1", null);
    assertThat(extra.isEmpty()).isTrue();

    extra.put("1", "one");
    Object before = extra.state();
    extra.put("1", "one");
    assertThat(extra.state()).isSameAs(before);
  }

  @Test void get_ignored_if_unconfigured() {
    assertThat(extra.get("three")).isNull();
  }

  @Test void get_null_if_not_set() {
    assertThat(extra.get("1")).isNull();
  }

  @Test void mergeStateKeepingOursOnConflict_bothEmpty() {
    Object before = extra.state();
    extra.mergeStateKeepingOursOnConflict(extra2);
    assertThat(before).isSameAs(extra.state());

    assertThat(extra.isEmpty()).isTrue();
  }

  @Test void mergeStateKeepingOursOnConflict_empty_nonEmpty() {
    extra2.put("1", "one");
    extra2.put("2", "two");

    Object before = extra.state();
    extra.mergeStateKeepingOursOnConflict(extra2);
    assertThat(before).isNotSameAs(extra.state());

    assertThat(extra.get("1")).isEqualTo("one");
    assertThat(extra.get("2")).isEqualTo("two");
  }

  @Test void mergeStateKeepingOursOnConflict_nonEmpty_empty() {
    extra.put("1", "one");
    extra.put("2", "two");

    Object before = extra.state();
    extra.mergeStateKeepingOursOnConflict(extra2);
    assertThat(before).isSameAs(extra.state());

    assertThat(extra.get("1")).isEqualTo("one");
    assertThat(extra.get("2")).isEqualTo("two");
  }

  @Test void mergeStateKeepingOursOnConflict_noConflict() {
    extra.put("1", "one");
    extra.put("2", "two");
    extra2.put("2", "two");

    Object before = extra.state();
    extra.mergeStateKeepingOursOnConflict(extra2);
    assertThat(before).isSameAs(extra.state());

    assertThat(extra.get("1")).isEqualTo("one");
    assertThat(extra.get("2")).isEqualTo("two");
  }

  @Test void mergeStateKeepingOursOnConflict_oursWinsOnConflict() {
    extra.put("1", "one");
    extra.put("2", "two");
    extra2.put("2", "one");

    Object before = extra.state();
    extra.mergeStateKeepingOursOnConflict(extra2);
    assertThat(before).isSameAs(extra.state());

    assertThat(extra.get("1")).isEqualTo("one");
    assertThat(extra.get("2")).isEqualTo("two");
  }

  /**
   * Ensures only key and value comparison are used in equals and hashCode. This makes sure we can
   * know if an extraction with extra is empty or not.
   */
  @Test void equalsAndHashCode() {
    // empty extraction is equivalent
    assertThat(factory.create())
        .isEqualTo(factory.create());
    assertThat(factory.create())
        .hasSameHashCodeAs(factory.create());

    extra.put("1", "one");
    extra.put("2", "two");

    BasicMapExtra extra2 = factory.create();
    extra2.put("1", "one");
    extra2.put("2", "two");

    // same extra are equivalent
    assertThat(extra).isEqualTo(extra2);
    assertThat(extra).hasSameHashCodeAs(extra2);

    // different values are not equivalent
    extra2.put("2", "three");
    assertThat(extra).isNotEqualTo(extra2);
    assertThat(extra.hashCode()).isNotEqualTo(extra2.hashCode());
  }

  @Test void keySet_constantWhenNotDynamic() {
    Set<String> withNoValues = extra.keySet();
    extra.put("1", "one");
    extra.put("3", "three");

    assertThat(extra.keySet())
        .isSameAs(withNoValues);
  }

  @Test void keySet_dynamic() {
    factory = new BasicMapExtra.FactoryBuilder()
        .addInitialKey("1")
        .maxDynamicEntries(32).build();
    extra = factory.create();
    extra2 = factory.create();

    assertThat(extra.keySet()).containsOnly("1");

    extra.put("2", "two");
    extra.put("3", "three");

    assertThat(extra.keySet()).containsOnly("1", "2", "3");

    extra.put("1", null);
    // Field one is not dynamic so it stays in the list
    assertThat(extra.keySet()).containsOnly("1", "2", "3");

    extra.put("2", null);
    // dynamic fields are also not pruned from the list
    assertThat(extra.keySet()).containsOnly("1", "2", "3");
  }

  @Test void putValue_ignores_if_not_defined() {
    extra.put("3", "three");

    assertThat(extra.isEmpty()).isTrue();
  }
}

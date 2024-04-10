/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentTracingTest {
  @BeforeEach void reset() {
    Tracing.CURRENT.set(null);
  }

  @AfterEach void close() {
    Tracing current = Tracing.current();
    if (current != null) current.close();
  }

  @Test void defaultsToNull() {
    assertThat(Tracing.current()).isNull();
  }

  @Test void defaultsToNull_currentTracer() {
    assertThat(Tracing.currentTracer()).isNull();
  }

  @Test void autoRegisters() {
    Tracing tracing = Tracing.newBuilder().build();

    assertThat(Tracing.current())
      .isSameAs(tracing);

    assertThat(Tracing.currentTracer())
      .isSameAs(tracing.tracer());
  }

  @Test void setsNotCurrentOnClose() {
    autoRegisters();

    Tracing.current().close();

    assertThat(Tracing.current()).isNull();
    assertThat(Tracing.currentTracer()).isNull();
  }

  @Test void canSetCurrentAgain() {
    setsNotCurrentOnClose();

    autoRegisters();
  }

  @Test void onlyRegistersOnce() throws InterruptedException {
    final Tracing[] threadValues = new Tracing[10]; // array ref for thread-safe setting

    List<Thread> getOrSet = new ArrayList<>(20);

    for (int i = 0; i < 10; i++) {
      final int index = i;
      getOrSet.add(new Thread(() -> threadValues[index] = Tracing.current()));
    }
    for (int i = 10; i < 20; i++) {
      getOrSet.add(new Thread(() -> Tracing.newBuilder().build().tracer()));
    }

    // make it less predictable
    Collections.shuffle(getOrSet);

    // start the races
    getOrSet.forEach(Thread::start);
    for (Thread thread : getOrSet) {
      thread.join();
    }

    Set<Tracing> tracers = new LinkedHashSet<>(Arrays.asList(threadValues));
    tracers.remove(null);
    // depending on race, we should have either one tracer or no tracer
    assertThat(tracers.isEmpty() || tracers.size() == 1)
      .isTrue();
  }
}

/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.http;

import brave.Tracing;
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
import static org.mockito.Mockito.mock;

// copy of tests in CurrentTracingTest as same pattern is used
public class CurrentHttpTracingTest {
  Tracing tracing = mock(Tracing.class);

  @BeforeEach void reset() {
    HttpTracing.CURRENT.set(null);
  }

  @AfterEach void close() {
    HttpTracing current = HttpTracing.current();
    if (current != null) current.close();
  }

  @Test void defaultsToNull() {
    assertThat(HttpTracing.current()).isNull();
  }

  @Test void autoRegisters() {
    HttpTracing current = HttpTracing.create(tracing);

    assertThat(HttpTracing.current())
      .isSameAs(current);
  }

  @Test void setsNotCurrentOnClose() {
    autoRegisters();

    HttpTracing.current().close();

    assertThat(HttpTracing.current()).isNull();
  }

  @Test void canSetCurrentAgain() {
    setsNotCurrentOnClose();

    autoRegisters();
  }

  @Test void onlyRegistersOnce() throws InterruptedException {
    final HttpTracing[] threadValues = new HttpTracing[10]; // array ref for thread-safe setting

    List<Thread> getOrSet = new ArrayList<>(20);

    for (int i = 0; i < 10; i++) {
      final int index = i;
      getOrSet.add(new Thread(() -> threadValues[index] = HttpTracing.current()));
    }
    for (int i = 10; i < 20; i++) {
      getOrSet.add(new Thread(() -> HttpTracing.create(tracing)));
    }

    // make it less predictable
    Collections.shuffle(getOrSet);

    // start the races
    getOrSet.forEach(Thread::start);
    for (Thread thread : getOrSet) {
      thread.join();
    }

    Set<HttpTracing> httpTracings = new LinkedHashSet<>(Arrays.asList(threadValues));
    httpTracings.remove(null);
    // depending on race, we should have either one instance or none
    assertThat(httpTracings.isEmpty() || httpTracings.size() == 1)
      .isTrue();
  }
}

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
package brave;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CurrentTracingTest {
  @Before
  public void reset() {
    Tracing.current = null;
  }

  @After public void close() {
    Tracing current = Tracing.current();
    if (current != null) current.close();
  }

  @Test public void defaultsToNull() {
    assertThat(Tracing.current()).isNull();
  }

  @Test public void defaultsToNull_currentTracer() {
    assertThat(Tracing.currentTracer()).isNull();
  }

  @Test public void autoRegisters() {
    Tracing tracing = Tracing.newBuilder().build();

    assertThat(Tracing.current())
      .isSameAs(tracing);

    assertThat(Tracing.currentTracer())
      .isSameAs(tracing.tracer());
  }

  @Test public void setsNotCurrentOnClose() {
    autoRegisters();

    Tracing.current().close();

    assertThat(Tracing.current()).isNull();
    assertThat(Tracing.currentTracer()).isNull();
  }

  @Test public void canSetCurrentAgain() {
    setsNotCurrentOnClose();

    autoRegisters();
  }

  @Test public void onlyRegistersOnce() throws InterruptedException {
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

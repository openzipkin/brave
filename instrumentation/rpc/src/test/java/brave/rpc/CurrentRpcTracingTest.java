/*
 * Copyright 2013-2023 The OpenZipkin Authors
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
package brave.rpc;

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
public class CurrentRpcTracingTest {
  Tracing tracing = mock(Tracing.class);

  @BeforeEach void reset() {
    RpcTracing.CURRENT.set(null);
  }

  @AfterEach void close() {
    RpcTracing current = RpcTracing.current();
    if (current != null) current.close();
  }

  @Test void defaultsToNull() {
    assertThat(RpcTracing.current()).isNull();
  }

  @Test void autoRegisters() {
    RpcTracing current = RpcTracing.create(tracing);

    assertThat(RpcTracing.current())
        .isSameAs(current);
  }

  @Test void setsNotCurrentOnClose() {
    autoRegisters();

    RpcTracing.current().close();

    assertThat(RpcTracing.current()).isNull();
  }

  @Test void canSetCurrentAgain() {
    setsNotCurrentOnClose();

    autoRegisters();
  }

  @Test void onlyRegistersOnce() throws InterruptedException {
    final RpcTracing[] threadValues = new RpcTracing[10]; // array ref for thread-safe setting

    List<Thread> getOrSet = new ArrayList<>(20);

    for (int i = 0; i < 10; i++) {
      final int index = i;
      getOrSet.add(new Thread(() -> threadValues[index] = RpcTracing.current()));
    }
    for (int i = 10; i < 20; i++) {
      getOrSet.add(new Thread(() -> RpcTracing.create(tracing)));
    }

    // make it less predictable
    Collections.shuffle(getOrSet);

    // start the races
    getOrSet.forEach(Thread::start);
    for (Thread thread : getOrSet) {
      thread.join();
    }

    Set<RpcTracing> rpcTracings = new LinkedHashSet<>(Arrays.asList(threadValues));
    rpcTracings.remove(null);
    // depending on race, we should have either one instance or none
    assertThat(rpcTracings.isEmpty() || rpcTracings.size() == 1)
        .isTrue();
  }
}

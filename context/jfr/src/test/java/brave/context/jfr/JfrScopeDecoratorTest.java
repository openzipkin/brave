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
package brave.context.jfr;

import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.StrictScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.propagation.TraceContext;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class JfrScopeDecoratorTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder();

  ExecutorService wrappedExecutor = Executors.newSingleThreadExecutor();
  CurrentTraceContext currentTraceContext = ThreadLocalCurrentTraceContext.newBuilder()
    .addScopeDecorator(StrictScopeDecorator.create())
    .addScopeDecorator(JfrScopeDecorator.create())
    .build();

  Executor executor = currentTraceContext.executor(wrappedExecutor);
  TraceContext context = TraceContext.newBuilder().traceId(1).spanId(1).build();
  TraceContext context2 = TraceContext.newBuilder().traceId(1).parentId(1).spanId(2).build();
  TraceContext context3 = TraceContext.newBuilder().traceId(2).spanId(3).build();

  @After public void shutdownExecutor() throws InterruptedException {
    wrappedExecutor.shutdown();
    wrappedExecutor.awaitTermination(1, TimeUnit.SECONDS);
  }

  @Test public void endToEndTest() throws Exception {
    Path destination = folder.newFile("execute.jfr").toPath();

    try (Recording recording = new Recording()) {
      recording.start();

      makeFiveScopes();

      recording.dump(destination);
    }

    List<RecordedEvent> events = RecordingFile.readAllEvents(destination);
    assertThat(events).extracting(e ->
      tuple(e.getString("traceId"), e.getString("parentId"), e.getString("spanId")))
      .containsExactlyInAnyOrder(
        tuple("0000000000000001", null, "0000000000000001"),
        tuple("0000000000000001", null, "0000000000000001"),
        tuple(null, null, null),
        tuple("0000000000000001", "0000000000000001", "0000000000000002"),
        tuple("0000000000000002", null, "0000000000000003")
      );
  }

  /**
   * This makes five scopes:
   *
   * <pre><ol>
   *   <li>Explicit scope 1</li>
   *   <li>Implicit scope 1 with a scoping executor</li>
   *   <li>Explicit scope 2 inside an executor thread</li>
   *   <li>Explicit clearing scope inside an executor thread</li>
   *   <li>Explicit scope 3 outside the executor thread</li>
   * </ol></pre>
   */
  void makeFiveScopes() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);

    try (Scope ws = currentTraceContext.newScope(context)) {
      executor.execute(() -> {
        try (Scope clear = currentTraceContext.newScope(null)) {
        }
        try (Scope child = currentTraceContext.newScope(context2)) {
          latch.countDown();
        }
      });
    }

    try (Scope ws = currentTraceContext.newScope(context3)) {
      latch.countDown();
      shutdownExecutor();
    }
  }
}

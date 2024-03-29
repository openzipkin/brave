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
package brave.propagation;

import brave.Tracing;
import brave.test.TestSpanHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ThreadLocalSpanTest {
  StrictCurrentTraceContext currentTraceContext = StrictCurrentTraceContext.create();
  TestSpanHandler spans = new TestSpanHandler();
  Tracing tracing = Tracing.newBuilder()
    .currentTraceContext(currentTraceContext)
    .addSpanHandler(spans)
    .build();

  ThreadLocalSpan threadLocalSpan = ThreadLocalSpan.create(tracing.tracer());

  @AfterEach void close() {
    tracing.close();
    currentTraceContext.close();
  }

  @Test void next() {
    assertThat(threadLocalSpan.next())
      .isEqualTo(threadLocalSpan.remove());
  }

  @Test void next_extracted() {
    assertThat(threadLocalSpan.next(TraceContextOrSamplingFlags.DEBUG))
      .isEqualTo(threadLocalSpan.remove());
  }
}

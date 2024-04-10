/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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

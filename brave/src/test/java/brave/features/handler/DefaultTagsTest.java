/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.features.handler;

import brave.ScopedSpan;
import brave.Tracing;
import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;
import brave.test.TestSpanHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * This shows how you can add a tag once per span as it enters a process. This is helpful for
 * environment details that are not request-specific, such as region.
 */
class DefaultTagsTest {
  TestSpanHandler spans = new TestSpanHandler();
  Tracing tracing = Tracing.newBuilder()
    .addSpanHandler(new SpanHandler() {
      @Override public boolean end(TraceContext context, MutableSpan span, Cause cause) {
        if (context.isLocalRoot()) {
          // pretend these are sourced from the environment
          span.tag("env", "prod");
          span.tag("region", "east");
        }
        return true;
      }
    })
    .addSpanHandler(spans)
    .build();

  @AfterEach void close() {
    tracing.close();
  }

  @Test void defaultTagsOnlyAddedOnce() {
    ScopedSpan parent = tracing.tracer().startScopedSpan("parent");
    try {
      tracing.tracer().startScopedSpan("child").finish();
    } finally {
      parent.finish();
    }

    assertThat(spans.get(0).name()).isEqualTo("child");
    assertThat(spans.get(0).tags()).isEmpty();

    assertThat(spans.get(1).name()).isEqualTo("parent");
    assertThat(spans.get(1).tags()).containsExactly(
      entry("env", "prod"),
      entry("region", "east")
    );
  }
}

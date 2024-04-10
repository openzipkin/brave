/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.no_deps;

import brave.ScopedSpan;
import brave.Tracing;
import brave.handler.SpanHandler;

import org.junit.jupiter.api.Test;

class TracingTest {
  @Test void basicUsage() {
    try (Tracing tracing = Tracing.newBuilder().addSpanHandler(new SpanHandler() {
      // avoid NOOP short-circuiting tracing
    }).build()) {
      ScopedSpan parent = tracing.tracer().startScopedSpan("parent");
      tracing.tracer().newChild(parent.context()).finish();
      parent.finish();
    }
  }
}

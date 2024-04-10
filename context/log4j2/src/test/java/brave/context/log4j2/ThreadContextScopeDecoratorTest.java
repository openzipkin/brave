/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.context.log4j2;

import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.propagation.TraceContext;
import brave.test.propagation.CurrentTraceContextTest;
import java.util.function.Supplier;
import org.apache.logging.log4j.ThreadContext;

import static org.assertj.core.api.Assertions.assertThat;

class ThreadContextScopeDecoratorTest extends CurrentTraceContextTest {
  @Override protected Class<? extends Supplier<CurrentTraceContext.Builder>> builderSupplier() {
    return BuilderSupplier.class;
  }

  static class BuilderSupplier implements Supplier<CurrentTraceContext.Builder> {
    @Override public CurrentTraceContext.Builder get() {
      return ThreadLocalCurrentTraceContext.newBuilder()
        .addScopeDecorator(ThreadContextScopeDecorator.newBuilder()
          .add(CORRELATION_FIELD).build());
    }
  }

  @Override protected void verifyImplicitContext(@Nullable TraceContext context) {
    if (context != null) {
      assertThat(ThreadContext.get("traceId")).isEqualTo(context.traceIdString());
      assertThat(ThreadContext.get("spanId")).isEqualTo(context.spanIdString());
      assertThat(ThreadContext.get(CORRELATION_FIELD.name()))
        .isEqualTo(CORRELATION_FIELD.baggageField().getValue(context));
    } else {
      assertThat(ThreadContext.get("traceId")).isNull();
      assertThat(ThreadContext.get("spanId")).isNull();
      assertThat(ThreadContext.get(CORRELATION_FIELD.name())).isNull();
    }
  }
}

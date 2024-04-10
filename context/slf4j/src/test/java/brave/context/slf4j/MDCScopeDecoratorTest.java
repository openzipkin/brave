/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.context.slf4j;

import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.propagation.TraceContext;
import brave.test.propagation.CurrentTraceContextTest;
import java.util.function.Supplier;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class MDCScopeDecoratorTest extends CurrentTraceContextTest {
  @Override protected Class<? extends Supplier<CurrentTraceContext.Builder>> builderSupplier() {
    return BuilderSupplier.class;
  }

  static class BuilderSupplier implements Supplier<CurrentTraceContext.Builder> {
    @Override public CurrentTraceContext.Builder get() {
      return ThreadLocalCurrentTraceContext.newBuilder()
        .addScopeDecorator(MDCScopeDecorator.newBuilder().add(CORRELATION_FIELD).build());
    }
  }

  @Override protected void verifyImplicitContext(@Nullable TraceContext context) {
    if (context != null) {
      assertThat(MDC.get("traceId")).isEqualTo(context.traceIdString());
      assertThat(MDC.get("spanId")).isEqualTo(context.spanIdString());
      assertThat(MDC.get(CORRELATION_FIELD.name()))
        .isEqualTo(CORRELATION_FIELD.baggageField().getValue(context));
    } else {
      assertThat(MDC.get("traceId")).isNull();
      assertThat(MDC.get("spanId")).isNull();
      assertThat(MDC.get(CORRELATION_FIELD.name())).isNull();
    }
  }
}

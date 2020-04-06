/*
 * Copyright 2013-2020 The OpenZipkin Authors
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
package brave.context.log4j2;

import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.propagation.TraceContext;
import brave.test.propagation.CurrentTraceContextTest;
import java.util.function.Supplier;
import org.apache.logging.log4j.ThreadContext;

import static org.assertj.core.api.Assertions.assertThat;

public class ThreadContextScopeDecoratorTest extends CurrentTraceContextTest {
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

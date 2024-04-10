/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.propagation;

import brave.test.propagation.CurrentTraceContextTest;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ThreadLocalCurrentTraceContextTest extends CurrentTraceContextTest {
  @Override protected Class<? extends Supplier<CurrentTraceContext.Builder>> builderSupplier() {
    return BuilderSupplier.class;
  }

  /** Since the default thread-local is static, this helps code avoid leaks made by others. */
  @Test void clear_unleaks() {
    currentTraceContext.newScope(context); // leak a scope

    assertThat(currentTraceContext.get()).isEqualTo(context);

    ((ThreadLocalCurrentTraceContext) currentTraceContext).clear();

    assertThat(currentTraceContext.get()).isNull();
  }

  static class BuilderSupplier implements Supplier<CurrentTraceContext.Builder> {
    @Override public CurrentTraceContext.Builder get() {
      return ThreadLocalCurrentTraceContext.newBuilder();
    }
  }
}

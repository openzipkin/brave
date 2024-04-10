/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.propagation;

import brave.test.propagation.CurrentTraceContextTest;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static brave.propagation.CurrentTraceContext.Default.INHERITABLE;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InheritableDefaultCurrentTraceContextTest extends CurrentTraceContextTest {
  @Override protected Class<? extends Supplier<CurrentTraceContext.Builder>> builderSupplier() {
    return BuilderSupplier.class;
  }

  static class BuilderSupplier implements Supplier<CurrentTraceContext.Builder> {
    @Override public CurrentTraceContext.Builder get() {
      return new ThreadLocalCurrentTraceContext.Builder(INHERITABLE);
    }
  }

  @Test protected void isnt_inheritable()  {
    assertThrows(AssertionError.class, () -> {
      super.isnt_inheritable();
    });
  }

  @Test void is_inheritable() throws Exception {
    super.is_inheritable(currentTraceContext);
  }

  @BeforeEach void ensureNoOtherTestsTaint() {
    INHERITABLE.set(null);
    CurrentTraceContext.Default.DEFAULT.set(null);
  }
}

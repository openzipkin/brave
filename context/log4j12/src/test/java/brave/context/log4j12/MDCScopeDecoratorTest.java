/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.context.log4j12;

import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.propagation.TraceContext;
import brave.test.propagation.CurrentTraceContextTest;
import java.util.function.Supplier;
import org.apache.log4j.MDC;
import org.apache.log4j.helpers.Loader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MDCScopeDecoratorTest extends CurrentTraceContextTest {
  public MDCScopeDecoratorTest() {
    assumeMDCWorks();
  }

  /** {@link Loader#isJava1()} inteprets "java.version" of "11" as true (aka Java 1.1) */
  static void assumeMDCWorks() {
    String realJavaVersion = System.getProperty("java.version");
    try {
      System.setProperty("java.version", "1.8");
      // Cast to Object to ensure we don't use an overload added after Log4J 1.2!
      MDC.put("foo", (Object) "bar");
      assumeThat(MDC.get("foo"))
        .withFailMessage("Couldn't verify MDC in general")
        .isEqualTo("bar");
    } finally {
      MDC.remove("foo");
      System.setProperty("java.version", realJavaVersion);
    }
  }

  @Override protected Class<? extends Supplier<CurrentTraceContext.Builder>> builderSupplier() {
    return BuilderSupplier.class;
  }

  static class BuilderSupplier implements Supplier<CurrentTraceContext.Builder> {
    @Override public CurrentTraceContext.Builder get() {
      return ThreadLocalCurrentTraceContext.newBuilder()
        .addScopeDecorator(MDCScopeDecorator.newBuilder().add(CORRELATION_FIELD).build());
    }
  }

  @Test // Log4J 1.2.x MDC is inheritable by default
  public void isnt_inheritable() throws Exception {
    assertThrows(AssertionError.class, () -> {
      super.isnt_inheritable();
    });
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


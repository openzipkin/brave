/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave;

import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;
import org.junit.jupiter.api.Test;

import static brave.test.util.ClassLoaders.assertRunIsUnloadable;
import static org.assertj.core.api.Assertions.assertThat;

class TracingClassLoaderTest {
  @Test void unloadable_afterClose() {
    assertRunIsUnloadable(ClosesTracing.class, getClass().getClassLoader());
  }

  static class ClosesTracing implements Runnable {
    @Override public void run() {
      try (Tracing tracing = Tracing.newBuilder().build()) {
      }
    }
  }

  @Test void unloadable_afterBasicUsage() {
    assertRunIsUnloadable(BasicUsage.class, getClass().getClassLoader());
  }

  static class BasicUsage implements Runnable {
    @Override public void run() {
      try (Tracing tracing = Tracing.newBuilder().build()) {
        tracing.tracer().newTrace().start().finish();
      }
    }
  }

  @Test void unloadable_forgetClose() {
    assertRunIsUnloadable(ForgetClose.class, getClass().getClassLoader());
  }

  static class ForgetClose implements Runnable {
    @Override public void run() {
      Tracing.newBuilder().build();
      assertThat(Tracing.current()).isNotNull();
    }
  }

  @Test void unloadable_withLoggingReporter() {
    assertRunIsUnloadable(UsingLoggingReporter.class, getClass().getClassLoader());
  }

  // This test will clutter output; it is somewhat difficult to avoid that and still run the test
  static class UsingLoggingReporter implements Runnable {
    @Override public void run() {
      Tracing.LogSpanHandler reporter = new Tracing.LogSpanHandler();
      TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).build();
      reporter.end(context, new MutableSpan(context, null), SpanHandler.Cause.FINISHED);
    }
  }
}

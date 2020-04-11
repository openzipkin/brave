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
package brave;

import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;
import org.junit.Test;

import static brave.test.util.ClassLoaders.assertRunIsUnloadable;
import static org.assertj.core.api.Assertions.assertThat;

public class TracingClassLoaderTest {
  @Test public void unloadable_afterClose() {
    assertRunIsUnloadable(ClosesTracing.class, getClass().getClassLoader());
  }

  static class ClosesTracing implements Runnable {
    @Override public void run() {
      try (Tracing tracing = Tracing.newBuilder().build()) {
      }
    }
  }

  @Test public void unloadable_afterBasicUsage() {
    assertRunIsUnloadable(BasicUsage.class, getClass().getClassLoader());
  }

  static class BasicUsage implements Runnable {
    @Override public void run() {
      try (Tracing tracing = Tracing.newBuilder().build()) {
        tracing.tracer().newTrace().start().finish();
      }
    }
  }

  @Test public void unloadable_forgetClose() {
    assertRunIsUnloadable(ForgetClose.class, getClass().getClassLoader());
  }

  static class ForgetClose implements Runnable {
    @Override public void run() {
      Tracing.newBuilder().build();
      assertThat(Tracing.current()).isNotNull();
    }
  }

  @Test public void unloadable_withLoggingReporter() {
    assertRunIsUnloadable(UsingLoggingReporter.class, getClass().getClassLoader());
  }

  // This test will clutter output; it is somewhat difficult to avoid that and still run the test
  static class UsingLoggingReporter implements Runnable {
    @Override public void run() {
      Tracing.LogSpanHandler reporter = new Tracing.LogSpanHandler();
      TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).build();
      reporter.end(context, new MutableSpan(context, null), SpanHandler.Cause.FINISH);
    }
  }
}

/*
 * Copyright 2013-2019 The OpenZipkin Authors
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

import org.junit.Test;
import zipkin2.reporter.Reporter;

import static brave.test.util.ClassLoaders.assertRunIsUnloadable;

public class TracingClassLoaderTest {

  @Test public void unloadable_afterClose() {
    assertRunIsUnloadable(ClosesTracing.class, getClass().getClassLoader());
  }

  static class ClosesTracing implements Runnable {
    @Override public void run() {
      try (Tracing tracing = Tracing.newBuilder().spanReporter(Reporter.NOOP).build()) {
      }
    }
  }

  @Test public void unloadable_afterBasicUsage() {
    assertRunIsUnloadable(BasicUsage.class, getClass().getClassLoader());
  }

  static class BasicUsage implements Runnable {
    @Override public void run() {
      try (Tracing tracing = Tracing.newBuilder().spanReporter(Reporter.NOOP).build()) {
        tracing.tracer().newTrace().start().finish();
      }
    }
  }
}

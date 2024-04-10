/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.http;

import brave.Tracing;
import org.junit.jupiter.api.Test;

import static brave.test.util.ClassLoaders.assertRunIsUnloadable;
import static org.assertj.core.api.Assertions.assertThat;

// Mockito tests eagerly log which triggers the log4j log manager, which then makes this run fail if
// run in the same JVM. The easy workaround is to move this to IT, which forces another JVM.
//
// Other workarounds:
// * Stop using log4j2 as we don't need it anyway
// * Stop using the log4j2 log manager, at least in this project
// * Do some engineering like this: https://stackoverflow.com/a/28657203/2232476
class ITHttpTracingClassLoader {
  @Test void unloadable_afterClose() {
    assertRunIsUnloadable(ClosesHttpTracing.class, getClass().getClassLoader());
  }

  static class ClosesHttpTracing implements Runnable {
    @Override public void run() {
      try (Tracing tracing = Tracing.newBuilder().build();
           HttpTracing httpTracing = HttpTracing.create(tracing)) {
      }
    }
  }

  @Test void unloadable_afterBasicUsage() {
    assertRunIsUnloadable(BasicUsage.class, getClass().getClassLoader());
  }

  static class BasicUsage implements Runnable {
    @Override public void run() {
      try (Tracing tracing = Tracing.newBuilder().build();
           HttpTracing httpTracing = HttpTracing.create(tracing)) {
        httpTracing.serverRequestSampler().trySample(null);
      }
    }
  }

  @Test void unloadable_forgetClose() {
    assertRunIsUnloadable(ForgetClose.class, getClass().getClassLoader());
  }

  static class ForgetClose implements Runnable {
    @Override public void run() {
      try (Tracing tracing = Tracing.newBuilder().build()) {
        HttpTracing.create(tracing);
        assertThat(HttpTracing.current()).isNotNull();
      }
    }
  }
}

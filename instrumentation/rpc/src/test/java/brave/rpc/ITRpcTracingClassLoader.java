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
package brave.rpc;

import brave.Tracing;
import org.junit.Test;
import zipkin2.reporter.Reporter;

import static brave.test.util.ClassLoaders.assertRunIsUnloadable;
import static org.assertj.core.api.Assertions.assertThat;

// Mockito tests eagerly log which triggers the log4j log manager, which then makes this run fail if
// run in the same JVM. The easy workaround is to move this to IT, which forces another JVM.
//
// Other workarounds:
// * Stop using log4j2 as we don't need it anyway
// * Stop using the log4j2 log manager, at least in this project
// * Do some engineering like this: https://stackoverflow.com/a/28657203/2232476
public class ITRpcTracingClassLoader {
  @Test public void unloadable_afterClose() {
    assertRunIsUnloadable(ClosesRpcTracing.class, getClass().getClassLoader());
  }

  static class ClosesRpcTracing implements Runnable {
    @Override public void run() {
      try (Tracing tracing = Tracing.newBuilder().spanReporter(Reporter.NOOP).build();
           RpcTracing rpcTracing = RpcTracing.create(tracing)) {
      }
    }
  }

  @Test public void unloadable_afterBasicUsage() {
    assertRunIsUnloadable(BasicUsage.class, getClass().getClassLoader());
  }

  static class BasicUsage implements Runnable {
    @Override public void run() {
      try (Tracing tracing = Tracing.newBuilder().spanReporter(Reporter.NOOP).build();
           RpcTracing rpcTracing = RpcTracing.create(tracing)) {
        rpcTracing.serverSampler().trySample(null);
      }
    }
  }

  @Test public void unloadable_forgetClose() {
    assertRunIsUnloadable(ForgetClose.class, getClass().getClassLoader());
  }

  static class ForgetClose implements Runnable {
    @Override public void run() {
      try (Tracing tracing = Tracing.newBuilder().spanReporter(Reporter.NOOP).build()) {
        RpcTracing.create(tracing);
        assertThat(RpcTracing.current()).isNotNull();
      }
    }
  }
}

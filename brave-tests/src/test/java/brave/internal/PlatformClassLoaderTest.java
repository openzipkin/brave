/*
 * Copyright 2013-2023 The OpenZipkin Authors
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
package brave.internal;

import brave.test.util.ClassLoaders;
import org.junit.jupiter.api.Test;

import static java.net.InetSocketAddress.createUnresolved;
import static org.assertj.core.api.Assertions.assertThat;

class PlatformClassLoaderTest {
  @Test void unloadable_afterGet() {
    assertRunIsUnloadable(GetPlatform.class);
  }

  static class GetPlatform implements Runnable {
    @Override public void run() {
      Platform platform = Platform.get();
      assertThat(platform).isNotNull();
    }
  }

  @Test void unloadable_afterGetLinkLocalIp() {
    assertRunIsUnloadable(GetPlatformLinkLocalIp.class);
  }

  static class GetPlatformLinkLocalIp implements Runnable {
    @Override public void run() {
      Platform platform = Platform.get();
      platform.linkLocalIp();
    }
  }

  @Test void unloadable_afterGetNextTraceIdHigh() {
    assertRunIsUnloadable(GetPlatformNextTraceIdHigh.class);
  }

  static class GetPlatformNextTraceIdHigh implements Runnable {
    @Override public void run() {
      Platform platform = Platform.get();
      assertThat(platform.nextTraceIdHigh()).isNotZero();
    }
  }

  @Test void unloadable_afterGetHostString() {
    assertRunIsUnloadable(GetPlatformHostString.class);
  }

  static class GetPlatformHostString implements Runnable {
    @Override public void run() {
      Platform platform = Platform.get();
      assertThat(platform.getHostString(createUnresolved("1.2.3.4", 0)))
        .isNotNull();
    }
  }

  @Test void unloadable_afterGetClock() {
    assertRunIsUnloadable(GetPlatformClock.class);
  }

  static class GetPlatformClock implements Runnable {
    @Override public void run() {
      Platform platform = Platform.get();
      assertThat(platform.clock().currentTimeMicroseconds())
        .isPositive();
    }
  }

  void assertRunIsUnloadable(Class<? extends Runnable> runnable) {
    ClassLoaders.assertRunIsUnloadable(runnable, getClass().getClassLoader());
  }
}

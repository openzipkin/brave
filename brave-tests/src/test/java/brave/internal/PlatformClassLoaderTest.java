/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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

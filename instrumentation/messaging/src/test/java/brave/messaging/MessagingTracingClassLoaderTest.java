/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.messaging;

import brave.Tracing;
import org.junit.jupiter.api.Test;

import static brave.test.util.ClassLoaders.assertRunIsUnloadable;
import static org.assertj.core.api.Assertions.assertThat;

public class MessagingTracingClassLoaderTest {
  @Test void unloadable_afterClose() {
    assertRunIsUnloadable(ClosesMessagingTracing.class, getClass().getClassLoader());
  }

  static class ClosesMessagingTracing implements Runnable {
    @Override public void run() {
      try (Tracing tracing = Tracing.newBuilder().build();
           MessagingTracing messagingTracing = MessagingTracing.create(tracing)) {
      }
    }
  }

  @Test void unloadable_afterBasicUsage() {
    assertRunIsUnloadable(BasicUsage.class, getClass().getClassLoader());
  }

  static class BasicUsage implements Runnable {
    @Override public void run() {
      try (Tracing tracing = Tracing.newBuilder().build();
           MessagingTracing messagingTracing = MessagingTracing.create(tracing)) {
        messagingTracing.consumerSampler().trySample(null);
      }
    }
  }

  @Test void unloadable_forgetClose() {
    assertRunIsUnloadable(ForgetClose.class, getClass().getClassLoader());
  }

  static class ForgetClose implements Runnable {
    @Override public void run() {
      try (Tracing tracing = Tracing.newBuilder().build()) {
        MessagingTracing.create(tracing);
        assertThat(MessagingTracing.current()).isNotNull();
      }
    }
  }
}

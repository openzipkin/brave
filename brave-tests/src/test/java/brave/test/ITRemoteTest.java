/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.test;

import brave.handler.MutableSpan;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ITRemoteTest {
  static final class ITRemoteDummy extends ITRemote {
  }

  @Test void tracer_includesClassName() {
    ITRemoteDummy itRemote = new ITRemoteDummy();
    itRemote.tracing.tracer().newTrace().start(1L).finish(1L);

    MutableSpan span = itRemote.testSpanHandler.takeLocalSpan();
    assertThat(span.localServiceName())
        .isEqualTo("ITRemoteDummy"); // much better than "unknown"
  }
}

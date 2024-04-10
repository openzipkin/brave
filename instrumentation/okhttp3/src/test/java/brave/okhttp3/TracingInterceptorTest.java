/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.okhttp3;

import brave.Span;
import brave.Tracing;
import okhttp3.Interceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TracingInterceptorTest {
  Tracing tracing = Tracing.newBuilder().build();
  @Mock Interceptor.Chain chain;
  @Mock Span span;

  @Test void parseRouteAddress_skipsOnNoop() {
    when(span.isNoop()).thenReturn(true);
    TracingInterceptor.parseRouteAddress(chain, span);

    verify(span).isNoop();
    verifyNoMoreInteractions(span);
  }

  @AfterEach void close() {
    tracing.close();
  }
}

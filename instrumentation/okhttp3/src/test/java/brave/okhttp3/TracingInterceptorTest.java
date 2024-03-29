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

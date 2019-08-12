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
package brave.okhttp3;

import brave.Span;
import brave.Tracing;
import brave.propagation.ThreadLocalCurrentTraceContext;
import okhttp3.Interceptor;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import zipkin2.reporter.Reporter;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TracingInterceptorTest {
  Tracing tracing = Tracing.newBuilder()
    .currentTraceContext(ThreadLocalCurrentTraceContext.create())
    .spanReporter(Reporter.NOOP)
    .build();
  @Mock Interceptor.Chain chain;
  @Mock Span span;

  @Test public void parseRouteAddress_skipsOnNoop() {
    when(span.isNoop()).thenReturn(true);
    TracingInterceptor.parseRouteAddress(chain, span);

    verify(span).isNoop();
    verifyNoMoreInteractions(span);
  }

  @After public void close() {
    tracing.close();
  }
}

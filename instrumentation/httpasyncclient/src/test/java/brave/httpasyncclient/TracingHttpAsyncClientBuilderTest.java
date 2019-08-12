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
package brave.httpasyncclient;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.apache.http.HttpHost;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TracingHttpAsyncClientBuilderTest {
  @Mock HttpRequestWrapper request;
  @Mock brave.Span span;

  @Test public void parseTargetAddress_skipsOnNoop() {
    when(span.isNoop()).thenReturn(true);

    TracingHttpAsyncClientBuilder.parseTargetAddress(request, span);

    verify(span).isNoop();
    verifyNoMoreInteractions(span);
  }

  @Test public void parseTargetAddress_prefersAddress() throws UnknownHostException {
    when(span.isNoop()).thenReturn(false);
    when(span.remoteIpAndPort("1.2.3.4", -1)).thenReturn(true);
    when(request.getTarget()).thenReturn(
      new HttpHost(InetAddress.getByName("1.2.3.4"), "3.4.5.6", -1, "http"));

    TracingHttpAsyncClientBuilder.parseTargetAddress(request, span);

    verify(span).isNoop();
    verify(span).remoteIpAndPort("1.2.3.4", -1);
    verifyNoMoreInteractions(span);
  }

  @Test public void parseTargetAddress_acceptsHostname() {
    when(span.isNoop()).thenReturn(false);
    when(request.getTarget()).thenReturn(new HttpHost("1.2.3.4"));

    TracingHttpAsyncClientBuilder.parseTargetAddress(request, span);

    verify(span).isNoop();
    verify(span).remoteIpAndPort("1.2.3.4", -1);
    verifyNoMoreInteractions(span);
  }

  @Test public void parseTargetAddress_IpAndPortFromHost() {
    when(span.isNoop()).thenReturn(false);
    when(span.remoteIpAndPort("1.2.3.4", 9999)).thenReturn(true);

    when(request.getTarget()).thenReturn(new HttpHost("1.2.3.4", 9999));

    TracingHttpAsyncClientBuilder.parseTargetAddress(request, span);

    verify(span).isNoop();
    verify(span).remoteIpAndPort("1.2.3.4", 9999);
    verifyNoMoreInteractions(span);
  }
}

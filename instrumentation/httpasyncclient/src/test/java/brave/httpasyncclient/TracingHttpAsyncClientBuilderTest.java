/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.httpasyncclient;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.apache.http.HttpHost;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TracingHttpAsyncClientBuilderTest {
  @Mock brave.Span span;

  @Test void parseTargetAddress_skipsOnNoop() {
    when(span.isNoop()).thenReturn(true);

    TracingHttpAsyncClientBuilder.parseTargetAddress(null, span);

    verify(span).isNoop();
    verifyNoMoreInteractions(span);
  }

  @Test void parseTargetAddress_prefersAddress() throws UnknownHostException {
    when(span.isNoop()).thenReturn(false);
    when(span.remoteIpAndPort("1.2.3.4", -1)).thenReturn(true);
    HttpHost host = new HttpHost(InetAddress.getByName("1.2.3.4"), "3.4.5.6", -1, "http");

    TracingHttpAsyncClientBuilder.parseTargetAddress(host, span);

    verify(span).isNoop();
    verify(span).remoteIpAndPort("1.2.3.4", -1);
    verifyNoMoreInteractions(span);
  }

  @Test void parseTargetAddress_acceptsHostname() {
    when(span.isNoop()).thenReturn(false);
    HttpHost host = new HttpHost("1.2.3.4");

    TracingHttpAsyncClientBuilder.parseTargetAddress(host, span);

    verify(span).isNoop();
    verify(span).remoteIpAndPort("1.2.3.4", -1);
    verifyNoMoreInteractions(span);
  }

  @Test void parseTargetAddress_IpAndPortFromHost() {
    when(span.isNoop()).thenReturn(false);
    when(span.remoteIpAndPort("1.2.3.4", 9999)).thenReturn(true);

    HttpHost host = new HttpHost("1.2.3.4", 9999);

    TracingHttpAsyncClientBuilder.parseTargetAddress(host, span);

    verify(span).isNoop();
    verify(span).remoteIpAndPort("1.2.3.4", 9999);
    verifyNoMoreInteractions(span);
  }
}

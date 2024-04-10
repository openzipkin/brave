/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.http;

import brave.Span;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class HttpServerRequestTest {
  @Mock HttpServerRequest serverRequest;
  @Mock Span span;

  @Test void parseClientIpAndPort_prefersXForwardedFor() {
    when(serverRequest.header("X-Forwarded-For")).thenReturn("1.2.3.4");

    when(serverRequest.parseClientIpAndPort(span)).thenCallRealMethod();
    when(serverRequest.parseClientIpFromXForwardedFor(span)).thenCallRealMethod();

    serverRequest.parseClientIpAndPort(span);

    verify(span).remoteIpAndPort("1.2.3.4", 0);
    verifyNoMoreInteractions(span);
  }

  @Test void parseClientIpAndPort_picksFirstXForwardedFor() {
    when(serverRequest.header("X-Forwarded-For")).thenReturn("1.2.3.4,3.4.5.6");

    when(serverRequest.parseClientIpAndPort(span)).thenCallRealMethod();
    when(serverRequest.parseClientIpFromXForwardedFor(span)).thenCallRealMethod();

    serverRequest.parseClientIpAndPort(span);

    verify(span).remoteIpAndPort("1.2.3.4", 0);
    verifyNoMoreInteractions(span);
  }

  @Test void parseClientIpAndPort_skipsOnNoIp() {
    when(serverRequest.parseClientIpAndPort(span)).thenCallRealMethod();
    when(serverRequest.parseClientIpFromXForwardedFor(span)).thenCallRealMethod();

    serverRequest.parseClientIpAndPort(span);

    verifyNoMoreInteractions(span);
  }
}

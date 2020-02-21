/*
 * Copyright 2013-2020 The OpenZipkin Authors
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
package brave.http;

import brave.Span;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpServerRequestTest {
  @Mock HttpServerRequest serverRequest;
  @Mock Span span;

  @Test public void parseClientIpAndPort_prefersXForwardedFor() {
    when(serverRequest.header("X-Forwarded-For")).thenReturn("1.2.3.4");

    when(serverRequest.parseClientIpAndPort(span)).thenCallRealMethod();
    when(serverRequest.parseClientIpFromXForwardedFor(span)).thenCallRealMethod();

    serverRequest.parseClientIpAndPort(span);

    verify(span).remoteIpAndPort("1.2.3.4", 0);
    verifyNoMoreInteractions(span);
  }

  @Test public void parseClientIpAndPort_picksFirstXForwardedFor() {
    when(serverRequest.header("X-Forwarded-For")).thenReturn("1.2.3.4,3.4.5.6");

    when(serverRequest.parseClientIpAndPort(span)).thenCallRealMethod();
    when(serverRequest.parseClientIpFromXForwardedFor(span)).thenCallRealMethod();

    serverRequest.parseClientIpAndPort(span);

    verify(span).remoteIpAndPort("1.2.3.4", 0);
    verifyNoMoreInteractions(span);
  }

  @Test public void parseClientIpAndPort_skipsOnNoIp() {
    when(serverRequest.parseClientIpAndPort(span)).thenCallRealMethod();
    when(serverRequest.parseClientIpFromXForwardedFor(span)).thenCallRealMethod();

    serverRequest.parseClientIpAndPort(span);

    verifyNoMoreInteractions(span);
  }
}

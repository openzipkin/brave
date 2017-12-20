/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package com.github.kristofa.brave.servlet.internal;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ThreadLocalServerClientAndLocalSpanState;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class MaybeAddClientAddressFromRequestTest {
  List<zipkin2.Span> spans = new ArrayList<>();
  Brave brave = new Brave.Builder().spanReporter(spans::add).build();

  @Before
  public void clearState() {
    ThreadLocalServerClientAndLocalSpanState.clear();
  }

  MaybeAddClientAddressFromRequest addressFunction = new MaybeAddClientAddressFromRequest(brave);

  HttpServletRequest request;

  @Before
  public void setup(){
    request = mock(HttpServletRequest.class);
  }

  @Test
  public void kickOutIfXForwardedForAndRemoteAddrAreInvalid() {
    for (String invalid : Arrays.asList(null, "unknown", "999.999.999")) {
      brave.serverTracer().setStateUnknown("get");

      when(request.getHeader("X-Forwarded-For")).thenReturn(invalid);
      when(request.getRemoteAddr()).thenReturn(invalid);

      // shouldn't throw exceptions
      addressFunction.accept(request);

      // shouldn't add anything to the span
      brave.serverTracer().setServerSend();
      assertThat(spans.get(0).remoteEndpoint()).isNull();
    }
  }

  @Test
  public void fallbackToRemoteAddrWhenXForwardedForIsInvalid() {
    brave.serverTracer().setStateUnknown("get");
    when(request.getHeader("X-Forwarded-For")).thenReturn("unknown");
    when(request.getRemoteAddr()).thenReturn("1.2.3.4");

    addressFunction.accept(request);
    brave.serverTracer().setServerSend();

    assertThat(spans.get(0).remoteEndpoint().ipv4())
        .isEqualTo("1.2.3.4");
  }

  @Test
  public void prefersXForwardedFor() {
    brave.serverTracer().setStateUnknown("get");
    when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4");
    when(request.getRemoteAddr()).thenReturn("5.6.7.8");

    addressFunction.accept(request);
    brave.serverTracer().setServerSend();

    assertThat(spans.get(0).remoteEndpoint().ipv4())
        .isEqualTo("1.2.3.4");
  }

  @Test
  public void parsesIpv6() throws UnknownHostException {
    brave.serverTracer().setStateUnknown("get");
    when(request.getHeader("X-Forwarded-For")).thenReturn("2001:db8::c001");

    addressFunction.accept(request);
    brave.serverTracer().setServerSend();

    assertThat(spans.get(0).remoteEndpoint().ipv6())
        .isEqualTo("2001:db8::c001");
  }

  @Test
  public void parsesIpv4MappedIpV6Address() {
    brave.serverTracer().setStateUnknown("get");
    when(request.getHeader("X-Forwarded-For")).thenReturn("::ffff:1.2.3.4");

    addressFunction.accept(request);
    brave.serverTracer().setServerSend();

    assertThat(spans.get(0).remoteEndpoint().ipv4())
        .isEqualTo("1.2.3.4");
  }

  @Test
  public void parsesIpv4CompatIpV6Address() {
    brave.serverTracer().setStateUnknown("get");
    when(request.getHeader("X-Forwarded-For")).thenReturn("::0000:1.2.3.4");

    addressFunction.accept(request);
    brave.serverTracer().setServerSend();

    assertThat(spans.get(0).remoteEndpoint().ipv4())
        .isEqualTo("1.2.3.4");
  }

  @Test
  public void addsRemotePort() {
    brave.serverTracer().setStateUnknown("get");
    when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4");
    when(request.getRemotePort()).thenReturn(124);

    addressFunction.accept(request);
    brave.serverTracer().setServerSend();

    assertThat(spans.get(0).remoteEndpoint().port())
        .isEqualTo(124);
  }
}

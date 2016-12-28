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
import com.github.kristofa.brave.ServerSpan;
import com.github.kristofa.brave.ServerSpanThreadBinder;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.internal.InternalSpan;
import com.twitter.zipkin.gen.Span;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.util.Arrays;
import javax.servlet.http.HttpServletRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public final class MaybeAddClientAddressFromRequestTest {
  static {
    InternalSpan.initializeInstanceForTests();
  }

  @Rule
  public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock
  HttpServletRequest request;
  @Mock
  ServerSpan serverSpan;
  Span span = InternalSpan.instance.newSpan(SpanId.builder().spanId(1L).build());
  @Mock
  Brave brave;
  @Mock
  ServerSpanThreadBinder threadBinder;
  MaybeAddClientAddressFromRequest addressFunction;

  @Before
  public void setup() {
    when(brave.serverSpanThreadBinder()).thenReturn(threadBinder);
    addressFunction = new MaybeAddClientAddressFromRequest(brave);
  }

  @Test
  public void kickOutIfXForwardedForAndRemoteAddrAreInvalid() {
    for (String invalid : Arrays.asList(null, "unknown", "999.999.999")) {
      when(threadBinder.getCurrentServerSpan()).thenReturn(serverSpan);
      when(serverSpan.getSpan()).thenReturn(span);
      when(request.getHeader("X-Forwarded-For")).thenReturn(invalid);
      when(request.getRemoteAddr()).thenReturn(invalid);

      // shouldn't throw exceptions
      addressFunction.accept(request);

      // shouldn't add anything to the span
      assertThat(span.getBinary_annotations())
          .isEmpty();
    }
  }

  @Test
  public void fallbackToRemoteAddrWhenXForwardedForIsInvalid() {
    when(threadBinder.getCurrentServerSpan()).thenReturn(serverSpan);
    when(serverSpan.getSpan()).thenReturn(span);
    when(request.getHeader("X-Forwarded-For")).thenReturn("unknown");
    when(request.getRemoteAddr()).thenReturn("1.2.3.4");

    addressFunction.accept(request);

    assertThat(span.getBinary_annotations()).extracting(b -> b.host.ipv4)
        .containsExactly(1 << 24 | 2 << 16 | 3 << 8 | 4);
  }

  @Test
  public void prefersXForwardedFor() {
    when(threadBinder.getCurrentServerSpan()).thenReturn(serverSpan);
    when(serverSpan.getSpan()).thenReturn(span);
    when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4");
    when(request.getRemoteAddr()).thenReturn("5.6.7.8");

    addressFunction.accept(request);

    assertThat(span.getBinary_annotations()).extracting(b -> b.host.ipv4)
        .containsExactly(1 << 24 | 2 << 16 | 3 << 8 | 4);
  }

  @Test
  public void parsesIpv6() throws UnknownHostException {
    when(threadBinder.getCurrentServerSpan()).thenReturn(serverSpan);
    when(serverSpan.getSpan()).thenReturn(span);
    when(request.getHeader("X-Forwarded-For")).thenReturn("2001:db8::c001");

    addressFunction.accept(request);

    assertThat(span.getBinary_annotations()).extracting(b -> b.host.ipv6)
        .containsExactly(Inet6Address.getByName("2001:db8::c001").getAddress());
  }

  @Test
  public void parsesIpv4MappedIpV6Address() {
    when(threadBinder.getCurrentServerSpan()).thenReturn(serverSpan);
    when(serverSpan.getSpan()).thenReturn(span);
    when(request.getHeader("X-Forwarded-For")).thenReturn("::ffff:1.2.3.4");

    addressFunction.accept(request);

    assertThat(span.getBinary_annotations()).extracting(b -> b.host.ipv4)
        .containsExactly(1 << 24 | 2 << 16 | 3 << 8 | 4);
  }

  @Test
  public void parsesIpv4CompatIpV6Address() {
    when(threadBinder.getCurrentServerSpan()).thenReturn(serverSpan);
    when(serverSpan.getSpan()).thenReturn(span);
    when(request.getHeader("X-Forwarded-For")).thenReturn("::0000:1.2.3.4");

    addressFunction.accept(request);

    assertThat(span.getBinary_annotations()).extracting(b -> b.host.ipv4)
        .containsExactly(1 << 24 | 2 << 16 | 3 << 8 | 4);
  }

  @Test
  public void addsRemotePort() {
    when(threadBinder.getCurrentServerSpan()).thenReturn(serverSpan);
    when(serverSpan.getSpan()).thenReturn(span);
    when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4");
    when(request.getRemotePort()).thenReturn(124);

    addressFunction.accept(request);

    assertThat(span.getBinary_annotations()).extracting(b -> b.host.port)
        .containsExactly((short) 124);
  }
}

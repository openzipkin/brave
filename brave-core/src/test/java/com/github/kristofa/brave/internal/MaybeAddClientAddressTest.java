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
package com.github.kristofa.brave.internal;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerSpan;
import com.github.kristofa.brave.ServerSpanThreadBinder;
import com.twitter.zipkin.gen.Span;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public final class MaybeAddClientAddressTest {
  @Rule
  public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock
  ServerSpan serverSpan;
  Span span = new Span();
  @Mock
  Brave brave;
  @Mock
  ServerSpanThreadBinder threadBinder;

  @Before
  public void setup(){
    when(brave.serverSpanThreadBinder()).thenReturn(threadBinder);
  }

  @Test
  public void kickOutIfNoServerSpan() {
    MaybeAddClientAddress function = new MaybeAddClientAddress(brave) {
      @Override protected byte[] parseAddressBytes(Object input) {
        throw new AssertionError();
      }

      @Override protected int parsePort(Object input) {
        throw new AssertionError();
      }
    };

    // shouldn't throw exceptions
    function.accept(new Object());
  }

  @Test
  public void kickOutIfNoServerSpan_Span() {
    when(threadBinder.getCurrentServerSpan()).thenReturn(serverSpan);

    MaybeAddClientAddress function = new MaybeAddClientAddress(brave) {
      @Override protected byte[] parseAddressBytes(Object input) {
        throw new AssertionError();
      }

      @Override protected int parsePort(Object input) {
        throw new AssertionError();
      }
    };

    // shouldn't throw exceptions
    function.accept(new Object());
  }

  @Test
  public void kickOutIfExceptionParsingAddress() {
    when(threadBinder.getCurrentServerSpan()).thenReturn(serverSpan);
    when(serverSpan.getSpan()).thenReturn(span);

    MaybeAddClientAddress function = new MaybeAddClientAddress(brave) {
      @Override protected byte[] parseAddressBytes(Object input) {
        throw new IllegalStateException();
      }

      @Override protected int parsePort(Object input) {
        throw new AssertionError();
      }
    };

    function.accept(new Object());

    // shouldn't add to span
    assertThat(span.getBinary_annotations()).isEmpty();
  }

  @Test
  public void kickOutIfNullAddress() {
    when(threadBinder.getCurrentServerSpan()).thenReturn(serverSpan);
    when(serverSpan.getSpan()).thenReturn(span);

    MaybeAddClientAddress function = new MaybeAddClientAddress(brave) {
      @Override protected byte[] parseAddressBytes(Object input) {
        return null;
      }

      @Override protected int parsePort(Object input) {
        throw new AssertionError();
      }
    };

    function.accept(new Object());

    // shouldn't add to span
    assertThat(span.getBinary_annotations()).isEmpty();
  }

  @Test
  public void kickOutIfInvalidAddress() {
    when(threadBinder.getCurrentServerSpan()).thenReturn(serverSpan);
    when(serverSpan.getSpan()).thenReturn(span);

    MaybeAddClientAddress function = new MaybeAddClientAddress(brave) {
      @Override protected byte[] parseAddressBytes(Object input) {
        return new byte[] {1, 2, 3, 4, 5};
      }

      @Override protected int parsePort(Object input) {
        throw new AssertionError();
      }
    };

    function.accept(new Object());

    // shouldn't add to span
    assertThat(span.getBinary_annotations()).isEmpty();
  }

  @Test
  public void ignoreExceptionParsingPort() {
    when(threadBinder.getCurrentServerSpan()).thenReturn(serverSpan);
    when(serverSpan.getSpan()).thenReturn(span);

    MaybeAddClientAddress function = new MaybeAddClientAddress(brave) {
      @Override protected byte[] parseAddressBytes(Object input) {
        return new byte[] {1, 2, 3, 4};
      }

      @Override protected int parsePort(Object input) {
        throw new IllegalStateException();
      }
    };

    function.accept(new Object());

    assertThat(span.getBinary_annotations()).extracting(b -> b.host.ipv4)
        .containsExactly(1 << 24 | 2 << 16 | 3 << 8 | 4);
  }

  // don't clutter the UI with bad client service names. we can revisit this topic later.
  @Test
  public void usesBlankServiceName() {
    when(threadBinder.getCurrentServerSpan()).thenReturn(serverSpan);
    when(serverSpan.getSpan()).thenReturn(span);

    MaybeAddClientAddress function = new MaybeAddClientAddress(brave) {
      @Override protected byte[] parseAddressBytes(Object input) {
        return new byte[] {1, 2, 3, 4};
      }

      @Override protected int parsePort(Object input) {
        return -1;
      }
    };

    function.accept(new Object());

    assertThat(span.getBinary_annotations()).extracting(b -> b.host.service_name)
        .containsExactly("");
  }

  @Test
  public void addsPort() {
    when(threadBinder.getCurrentServerSpan()).thenReturn(serverSpan);
    when(serverSpan.getSpan()).thenReturn(span);

    MaybeAddClientAddress function = new MaybeAddClientAddress(brave) {
      @Override protected byte[] parseAddressBytes(Object input) {
        return new byte[] {1, 2, 3, 4};
      }

      @Override protected int parsePort(Object input) {
        return 8080;
      }
    };

    function.accept(new Object());

    assertThat(span.getBinary_annotations()).extracting(b -> b.host.port)
        .containsExactly((short) 8080);
  }

  @Test
  public void ignoresNegativePort() {
    when(threadBinder.getCurrentServerSpan()).thenReturn(serverSpan);
    when(serverSpan.getSpan()).thenReturn(span);

    MaybeAddClientAddress function = new MaybeAddClientAddress(brave) {
      @Override protected byte[] parseAddressBytes(Object input) {
        return new byte[] {1, 2, 3, 4};
      }

      @Override protected int parsePort(Object input) {
        return -1;
      }
    };

    function.accept(new Object());

    assertThat(span.getBinary_annotations()).extracting(b -> b.host.port)
        .containsNull();
  }

  @Test
  public void acceptsIpv6() throws UnknownHostException {
    when(threadBinder.getCurrentServerSpan()).thenReturn(serverSpan);
    when(serverSpan.getSpan()).thenReturn(span);

    final byte[] ipv6 = Inet6Address.getByName("2001:db8::c001").getAddress();

    MaybeAddClientAddress function = new MaybeAddClientAddress(brave) {
      @Override protected byte[] parseAddressBytes(Object input) {
        return ipv6;
      }

      @Override protected int parsePort(Object input) {
        return -1;
      }
    };

    function.accept(new Object());

    assertThat(span.getBinary_annotations()).extracting(b -> b.host.ipv4)
        .containsExactly(0);
    assertThat(span.getBinary_annotations()).extracting(b -> b.host.ipv6)
        .containsExactly(ipv6);
  }

  @Test
  public void acceptsIpv4MappedIpV6Address() throws UnknownHostException {
    when(threadBinder.getCurrentServerSpan()).thenReturn(serverSpan);
    when(serverSpan.getSpan()).thenReturn(span);

    MaybeAddClientAddress function = new MaybeAddClientAddress(brave) {
      @Override protected byte[] parseAddressBytes(Object input) {
        return InetAddresses.ipStringToBytes("::ffff:1.2.3.4");
      }

      @Override protected int parsePort(Object input) {
        return -1;
      }
    };

    function.accept(new Object());

    assertThat(span.getBinary_annotations()).extracting(b -> b.host.ipv4)
        .containsExactly(1 << 24 | 2 << 16 | 3 << 8 | 4);
    assertThat(span.getBinary_annotations()).extracting(b -> b.host.ipv6)
        .containsNull();
  }

  @Test
  public void acceptsIpv4CompatIpV6Address() throws UnknownHostException {
    when(threadBinder.getCurrentServerSpan()).thenReturn(serverSpan);
    when(serverSpan.getSpan()).thenReturn(span);

    MaybeAddClientAddress function = new MaybeAddClientAddress(brave) {
      @Override protected byte[] parseAddressBytes(Object input) {
        return InetAddresses.ipStringToBytes("::0000:1.2.3.4");
      }

      @Override protected int parsePort(Object input) {
        return -1;
      }
    };

    function.accept(new Object());

    assertThat(span.getBinary_annotations()).extracting(b -> b.host.ipv4)
        .containsExactly(1 << 24 | 2 << 16 | 3 << 8 | 4);
    assertThat(span.getBinary_annotations()).extracting(b -> b.host.ipv6)
        .containsNull();
  }
}

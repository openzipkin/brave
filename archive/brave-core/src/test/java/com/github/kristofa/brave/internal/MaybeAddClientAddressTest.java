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
import com.github.kristofa.brave.ThreadLocalServerClientAndLocalSpanState;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import zipkin.Span;

import static org.assertj.core.api.Assertions.assertThat;

public class MaybeAddClientAddressTest {
  protected List<Span> spans = new ArrayList<>();
  protected Brave brave = new Brave.Builder().reporter(spans::add).build();

  @Before
  public void clearState() {
    ThreadLocalServerClientAndLocalSpanState.clear();
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
    brave.serverTracer().setStateUnknown("foo");
    brave.serverTracer().setServerReceived();

    MaybeAddClientAddress function = new MaybeAddClientAddress(brave) {
      @Override protected byte[] parseAddressBytes(Object input) {
        throw new IllegalStateException();
      }

      @Override protected int parsePort(Object input) {
        throw new AssertionError();
      }
    };

    function.accept(new Object());
    brave.serverTracer().setServerSend();

    // shouldn't add to span
    assertThat(spans.get(0).binaryAnnotations).isEmpty();
  }

  @Test
  public void kickOutIfNullAddress() {
    brave.serverTracer().setStateUnknown("foo");
    brave.serverTracer().setServerReceived();

    MaybeAddClientAddress function = new MaybeAddClientAddress(brave) {
      @Override protected byte[] parseAddressBytes(Object input) {
        return null;
      }

      @Override protected int parsePort(Object input) {
        throw new AssertionError();
      }
    };

    function.accept(new Object());
    brave.serverTracer().setServerSend();

    // shouldn't add to span
    assertThat(spans.get(0).binaryAnnotations).isEmpty();
  }

  @Test
  public void kickOutIfInvalidAddress() {
    brave.serverTracer().setStateUnknown("foo");
    brave.serverTracer().setServerReceived();

    MaybeAddClientAddress function = new MaybeAddClientAddress(brave) {
      @Override protected byte[] parseAddressBytes(Object input) {
        return new byte[] {1, 2, 3, 4, 5};
      }

      @Override protected int parsePort(Object input) {
        throw new AssertionError();
      }
    };

    function.accept(new Object());
    brave.serverTracer().setServerSend();

    // shouldn't add to span
    assertThat(spans.get(0).binaryAnnotations).isEmpty();
  }

  @Test
  public void ignoreExceptionParsingPort() {
    brave.serverTracer().setStateUnknown("foo");
    brave.serverTracer().setServerReceived();

    MaybeAddClientAddress function = new MaybeAddClientAddress(brave) {
      @Override protected byte[] parseAddressBytes(Object input) {
        return new byte[] {1, 2, 3, 4};
      }

      @Override protected int parsePort(Object input) {
        throw new IllegalStateException();
      }
    };

    function.accept(new Object());
    brave.serverTracer().setServerSend();

    assertThat(spans.get(0).binaryAnnotations).extracting(b -> b.endpoint.ipv4)
        .containsExactly(1 << 24 | 2 << 16 | 3 << 8 | 4);
  }

  // don't clutter the UI with bad client service names. we can revisit this topic later.
  @Test
  public void usesBlankServiceName() {
    brave.serverTracer().setStateUnknown("foo");
    brave.serverTracer().setServerReceived();

    MaybeAddClientAddress function = new MaybeAddClientAddress(brave) {
      @Override protected byte[] parseAddressBytes(Object input) {
        return new byte[] {1, 2, 3, 4};
      }

      @Override protected int parsePort(Object input) {
        return -1;
      }
    };

    function.accept(new Object());
    brave.serverTracer().setServerSend();

    assertThat(spans.get(0).binaryAnnotations).extracting(b -> b.endpoint.serviceName)
        .containsExactly("");
  }

  @Test
  public void addsPort() {
    brave.serverTracer().setStateUnknown("foo");
    brave.serverTracer().setServerReceived();

    MaybeAddClientAddress function = new MaybeAddClientAddress(brave) {
      @Override protected byte[] parseAddressBytes(Object input) {
        return new byte[] {1, 2, 3, 4};
      }

      @Override protected int parsePort(Object input) {
        return 8080;
      }
    };

    function.accept(new Object());
    brave.serverTracer().setServerSend();

    assertThat(spans.get(0).binaryAnnotations).extracting(b -> b.endpoint.port)
        .containsExactly((short) 8080);
  }

  @Test
  public void ignoresNegativePort() {
    brave.serverTracer().setStateUnknown("foo");

    MaybeAddClientAddress function = new MaybeAddClientAddress(brave) {
      @Override protected byte[] parseAddressBytes(Object input) {
        return new byte[] {1, 2, 3, 4};
      }

      @Override protected int parsePort(Object input) {
        return -1;
      }
    };

    function.accept(new Object());
    brave.serverTracer().setServerSend();

    assertThat(spans.get(0).binaryAnnotations).extracting(b -> b.endpoint.port)
        .containsNull();
  }

  @Test
  public void acceptsIpv6() throws UnknownHostException {
    brave.serverTracer().setStateUnknown("foo");
    brave.serverTracer().setServerReceived();

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
    brave.serverTracer().setServerSend();

    assertThat(spans.get(0).binaryAnnotations).extracting(b -> b.endpoint.ipv4)
        .containsExactly(0);
    assertThat(spans.get(0).binaryAnnotations).extracting(b -> b.endpoint.ipv6)
        .containsExactly(ipv6);
  }

  @Test
  public void acceptsIpv4MappedIpV6Address() throws UnknownHostException {
    brave.serverTracer().setStateUnknown("foo");
    brave.serverTracer().setServerReceived();

    MaybeAddClientAddress function = new MaybeAddClientAddress(brave) {
      @Override protected byte[] parseAddressBytes(Object input) {
        return InetAddresses.ipStringToBytes("::ffff:1.2.3.4");
      }

      @Override protected int parsePort(Object input) {
        return -1;
      }
    };

    function.accept(new Object());
    brave.serverTracer().setServerSend();

    assertThat(spans.get(0).binaryAnnotations).extracting(b -> b.endpoint.ipv4)
        .containsExactly(1 << 24 | 2 << 16 | 3 << 8 | 4);
    assertThat(spans.get(0).binaryAnnotations).extracting(b -> b.endpoint.ipv6)
        .containsNull();
  }

  @Test
  public void acceptsIpv4CompatIpV6Address() throws UnknownHostException {
    brave.serverTracer().setStateUnknown("foo");
    brave.serverTracer().setServerReceived();

    MaybeAddClientAddress function = new MaybeAddClientAddress(brave) {
      @Override protected byte[] parseAddressBytes(Object input) {
        return InetAddresses.ipStringToBytes("::0000:1.2.3.4");
      }

      @Override protected int parsePort(Object input) {
        return -1;
      }
    };

    function.accept(new Object());
    brave.serverTracer().setServerSend();

    assertThat(spans.get(0).binaryAnnotations).extracting(b -> b.endpoint.ipv4)
        .containsExactly(1 << 24 | 2 << 16 | 3 << 8 | 4);
    assertThat(spans.get(0).binaryAnnotations).extracting(b -> b.endpoint.ipv6)
        .containsNull();
  }

  /** This ensures we don't mistake IPv6 localhost for a mapped IPv4 0.0.0.1 */
  @Test
  public void acceptsIpV6Localhost() throws UnknownHostException {
    brave.serverTracer().setStateUnknown("foo");
    brave.serverTracer().setServerReceived();

    MaybeAddClientAddress function = new MaybeAddClientAddress(brave) {
      @Override protected byte[] parseAddressBytes(Object input) {
        return InetAddresses.ipStringToBytes("::1");
      }

      @Override protected int parsePort(Object input) {
        return -1;
      }
    };

    function.accept(new Object());
    brave.serverTracer().setServerSend();

    assertThat(spans.get(0).binaryAnnotations).extracting(b -> b.endpoint.ipv4)
        .containsExactly(0);
    byte[] ipv6_localhost = new byte[16];
    ipv6_localhost[15]= 1;
    assertThat(spans.get(0).binaryAnnotations).extracting(b -> b.endpoint.ipv6)
        .containsExactly(ipv6_localhost);
  }
}

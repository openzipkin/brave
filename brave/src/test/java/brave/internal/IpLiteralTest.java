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
package brave.internal;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IpLiteralTest {

  @Test public void ipOrNull_invalid() {
    assertThat(IpLiteral.ipOrNull("ahola")).isNull();
    assertThat(IpLiteral.ipOrNull("/43.0.292.2")).isNull();
    assertThat(IpLiteral.ipOrNull("43.0.292.2")).isNull();
    assertThat(IpLiteral.ipOrNull("::0000:43.0.192.302")).isNull();
    assertThat(IpLiteral.ipOrNull("200000:43.0.192.202")).isNull();
    assertThat(IpLiteral.ipOrNull("2001:db8::,c001")).isNull();
    assertThat(IpLiteral.ipOrNull("2001db8c001")).isNull();
  }

  @Test public void ipOrNull_ipv4() {
    assertThat(IpLiteral.ipOrNull("43.0.192.2")).isEqualTo("43.0.192.2");
  }

  @Test public void ipOrNull_ipv6() {
    assertThat(IpLiteral.ipOrNull("2001:db8::c001")).isEqualTo("2001:db8::c001");
  }

  @Test public void ipOrNull_ipv6_mappedIpv4() {
    assertThat(IpLiteral.ipOrNull("::FFFF:43.0.192.2")).isEqualTo("43.0.192.2");
  }

  @Test public void ipOrNull_ipv6_compatIpv4() {
    assertThat(IpLiteral.ipOrNull("::0000:43.0.192.2")).isEqualTo("43.0.192.2");
  }

  @Test public void ipOrNullv6_notMappedIpv4() {
    assertThat(IpLiteral.ipOrNull("::ffef:43.0.192.2")).isNull();
  }

  @Test public void ipOrNull_ipv6UpperCase() {
    // TODO: downcase
    assertThat(IpLiteral.ipOrNull("2001:DB8::C001")).isEqualTo("2001:DB8::C001");
  }

  @Test public void ipOrNull_ipv6_compatIpv4_compressed() {
    assertThat(IpLiteral.ipOrNull("::43.0.192.2")).isEqualTo("43.0.192.2");
  }

  /** This ensures we don't mistake IPv6 localhost for a mapped IPv4 0.0.0.1 */
  @Test public void ipOrNullv6_localhost() {
    assertThat(IpLiteral.ipOrNull("::1")).isEqualTo("::1");
  }

  @Test public void ipOrNullv4_localhost() {
    assertThat(IpLiteral.ipOrNull("127.0.0.1")).isEqualTo("127.0.0.1");
  }

  /** This is an unusable compat Ipv4 of 0.0.0.2. This makes sure it isn't mistaken for localhost */
  @Test public void ipOrNullv6_notLocalhost() {
    assertThat(IpLiteral.ipOrNull("::2")).isEqualTo("::2");
  }
}

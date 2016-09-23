package com.twitter.zipkin.gen;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

public class EndpointTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void messageWhenMissingServiceName() {
    thrown.expect(NullPointerException.class);
    thrown.expectMessage("serviceName");

    Endpoint.builder().ipv4(127 << 24 | 1).build();
  }

  @Test
  public void missingIpv4CoercesTo0() {
    assertThat(Endpoint.builder().serviceName("foo").build().ipv4)
        .isEqualTo(0);
  }

  @Test
  public void builderWithPort_0CoercesToNull() {
    assertThat(Endpoint.builder().serviceName("foo").port(0).build().port)
        .isNull();
  }

  @Test
  public void builderWithPort_highest() {
    assertThat(Endpoint.builder().serviceName("foo").port(65535).build().port)
        .isEqualTo((short) -1); // an unsigned short of 65535 is the same as -1
  }

  /** The integer arg of port should be a whole number */
  @Test
  public void builderWithPort_negativeIsInvalid() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("invalid port -1");

    assertThat(Endpoint.builder().serviceName("foo").port(-1).build().port);
  }

  /** The integer arg of port should fit in a 16bit unsigned value */
  @Test
  public void builderWithPort_tooHighIsInvalid() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("invalid port 65536");

    assertThat(Endpoint.builder().serviceName("foo").port(65536).build().port);
  }

  @Test
  public void lowercasesServiceName() {
    assertThat(Endpoint.builder().serviceName("fFf").ipv4(127 << 24 | 1).build().service_name)
        .isEqualTo("fff");
  }
}

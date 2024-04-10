/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jaxrs2;

import brave.jaxrs2.TracingClientFilter.ClientResponseContextWrapper;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ClientResponseContextWrapperTest {
  @Mock ClientRequestContext request;
  @Mock ClientResponseContext response;

  @Test void request() {
    assertThat(new ClientResponseContextWrapper(request, response).request().unwrap())
      .isSameAs(request);
  }

  @Test void statusCode() {
    when(response.getStatus()).thenReturn(200);

    assertThat(new ClientResponseContextWrapper(request, response).statusCode()).isEqualTo(200);
  }

  @Test void statusCode_zeroWhenNegative() {
    when(response.getStatus()).thenReturn(-1);

    assertThat(new ClientResponseContextWrapper(request, response).statusCode()).isZero();
  }
}

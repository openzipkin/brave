/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jakarta.jersey.server;

import brave.jakarta.jersey.server.TracingApplicationEventListener.RequestEventWrapper;
import jakarta.ws.rs.ClientErrorException;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.internal.process.MappableException;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RequestEventWrapperTest {
  @Mock ContainerRequest request;
  @Mock RequestEvent event;
  @Mock ContainerResponse response;

  @Test void method() {
    when(event.getContainerRequest()).thenReturn(request);
    when(request.getMethod()).thenReturn("GET");

    assertThat(new RequestEventWrapper(event).method())
      .isEqualTo("GET");
  }

  @Test void request() {
    when(event.getContainerRequest()).thenReturn(request);

    assertThat(new RequestEventWrapper(event).request().unwrap())
      .isSameAs(request);
  }

  @Test void statusCode() {
    when(event.getContainerResponse()).thenReturn(response);
    when(response.getStatus()).thenReturn(200);

    assertThat(new RequestEventWrapper(event).statusCode()).isEqualTo(200);
  }

  @Test void statusCode_exception() {
    when(event.getException()).thenReturn(new ClientErrorException(400));

    assertThat(new RequestEventWrapper(event).statusCode()).isEqualTo(400);
  }

  @Test void statusCode_mappableException() {
    when(event.getException()).thenReturn(new MappableException(new ClientErrorException(400)));

    assertThat(new RequestEventWrapper(event).statusCode()).isEqualTo(400);
  }

  @Test void statusCode_zeroNoResponse() {
    assertThat(new RequestEventWrapper(event).statusCode()).isZero();
  }
}

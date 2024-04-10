/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.vertx.web;

import brave.vertx.web.TracingRoutingContextHandler.HttpServerResponseWrapper;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // TODO: hunt down these
public class HttpServerResponseWrapperTest {
  @Mock RoutingContext context;
  @Mock HttpServerRequest request;
  @Mock HttpServerResponse response;
  @Mock Route currentRoute;

  @BeforeEach void setup() {
    when(context.request()).thenReturn(request);
    when(context.response()).thenReturn(response);
    when(context.currentRoute()).thenReturn(currentRoute);
  }

  @Test void request() {
    assertThat(new HttpServerResponseWrapper(context).request().unwrap())
      .isSameAs(request);
  }

  @Test void method() {
    when(request.rawMethod()).thenReturn("GET");

    assertThat(new HttpServerResponseWrapper(context).method())
      .isEqualTo("GET");
  }

  @Test void route_emptyByDefault() {
    assertThat(new HttpServerResponseWrapper(context).route())
      .isEmpty();
  }

  @Test void route() {
    when(currentRoute.getPath()).thenReturn("/users/:userID");

    assertThat(new HttpServerResponseWrapper(context).route())
      .isEqualTo("/users/:userID");
  }

  @Test void statusCode() {
    when(response.getStatusCode()).thenReturn(200);

    assertThat(new HttpServerResponseWrapper(context).statusCode())
      .isEqualTo(200);
  }

  @Test void statusCode_zero() {
    assertThat(new HttpServerResponseWrapper(context).statusCode())
      .isZero();
  }
}

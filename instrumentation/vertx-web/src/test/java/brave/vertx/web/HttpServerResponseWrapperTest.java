/*
 * Copyright 2013-2023 The OpenZipkin Authors
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

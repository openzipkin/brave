/*
 * Copyright 2013-2020 The OpenZipkin Authors
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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpServerResponseWrapperTest {
  @Mock RoutingContext context;
  @Mock HttpServerRequest request;
  @Mock HttpServerResponse response;
  @Mock Route currentRoute;

  @Before public void setup() {
    when(context.request()).thenReturn(request);
    when(context.response()).thenReturn(response);
    when(context.currentRoute()).thenReturn(currentRoute);
  }

  @Test public void request() {
    assertThat(new HttpServerResponseWrapper(context).request().unwrap())
      .isSameAs(request);
  }

  @Test public void method() {
    when(request.rawMethod()).thenReturn("GET");

    assertThat(new HttpServerResponseWrapper(context).method())
      .isEqualTo("GET");
  }

  @Test public void route_emptyByDefault() {
    assertThat(new HttpServerResponseWrapper(context).route())
      .isEmpty();
  }

  @Test public void route() {
    when(currentRoute.getPath()).thenReturn("/users/:userID");

    assertThat(new HttpServerResponseWrapper(context).route())
      .isEqualTo("/users/:userID");
  }

  @Test public void statusCode() {
    when(response.getStatusCode()).thenReturn(200);

    assertThat(new HttpServerResponseWrapper(context).statusCode())
      .isEqualTo(200);
  }

  @Test public void statusCode_zero() {
    assertThat(new HttpServerResponseWrapper(context).statusCode())
      .isZero();
  }
}

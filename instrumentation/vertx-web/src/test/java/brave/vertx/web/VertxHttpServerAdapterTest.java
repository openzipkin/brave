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
package brave.vertx.web;

import io.vertx.core.http.HttpServerResponse;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static brave.test.util.ClassLoaders.assertRunIsUnloadable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class VertxHttpServerAdapterTest {
  @Mock HttpServerResponse response;
  VertxHttpServerAdapter adapter = new VertxHttpServerAdapter();

  @After public void clear() {
    VertxHttpServerAdapter.METHOD_AND_PATH.remove();
  }

  @Test public void methodFromResponse() {
    VertxHttpServerAdapter.setCurrentMethodAndPath("GET", null);

    assertThat(adapter.methodFromResponse(response))
      .isEqualTo("GET");
  }

  @Test public void route_emptyByDefault() {
    VertxHttpServerAdapter.setCurrentMethodAndPath("GET", null);

    assertThat(adapter.route(response)).isEmpty();
  }

  @Test public void route() {
    VertxHttpServerAdapter.setCurrentMethodAndPath("GET", "/users/:userID");

    assertThat(adapter.route(response))
      .isEqualTo("/users/:userID");
  }

  @Test public void setCurrentMethodAndPath_doesntPreventClassUnloading() {
    assertRunIsUnloadable(MethodFromResponse.class, getClass().getClassLoader());
  }

  @Test public void statusCodeAsInt() {
    when(response.getStatusCode()).thenReturn(200);

    assertThat(adapter.statusCodeAsInt(response)).isEqualTo(200);
    assertThat(adapter.statusCode(response)).isEqualTo(200);
  }

  @Test public void statusCodeAsInt_zero() {
    assertThat(adapter.statusCodeAsInt(response)).isZero();
    assertThat(adapter.statusCode(response)).isNull();
  }

  static class MethodFromResponse implements Runnable {
    final VertxHttpServerAdapter adapter = new VertxHttpServerAdapter();

    @Override public void run() {
      VertxHttpServerAdapter.setCurrentMethodAndPath("GET", null);
      adapter.methodFromResponse(null);
    }
  }
}

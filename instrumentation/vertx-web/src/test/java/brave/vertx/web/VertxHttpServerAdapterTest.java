/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brave.vertx.web;

import io.vertx.core.http.HttpServerResponse;
import java.lang.reflect.Proxy;
import org.junit.After;
import org.junit.Test;

import static brave.test.util.ClassLoaders.assertRunIsUnloadable;
import static org.assertj.core.api.Assertions.assertThat;

public class VertxHttpServerAdapterTest {
  VertxHttpServerAdapter adapter = new VertxHttpServerAdapter();

  @After public void clear() {
    VertxHttpServerAdapter.METHOD_AND_PATH.remove();
  }

  @Test public void methodFromResponse() {
    HttpServerResponse response = dummyResponse();

    VertxHttpServerAdapter.setCurrentMethodAndPath("GET", null);

    assertThat(adapter.methodFromResponse(response))
        .isEqualTo("GET");
  }

  @Test public void route_emptyByDefault() {
    HttpServerResponse response = dummyResponse();

    VertxHttpServerAdapter.setCurrentMethodAndPath("GET", null);

    assertThat(adapter.route(response)).isEmpty();
  }

  @Test public void route() {
    HttpServerResponse response = dummyResponse();

    VertxHttpServerAdapter.setCurrentMethodAndPath("GET", "/users/:userID");

    assertThat(adapter.route(response))
        .isEqualTo("/users/:userID");
  }

  @Test public void setCurrentMethodAndPath_doesntPreventClassUnloading() {
    assertRunIsUnloadable(MethodFromResponse.class, getClass().getClassLoader());
  }

  static class MethodFromResponse implements Runnable {
    final VertxHttpServerAdapter adapter = new VertxHttpServerAdapter();

    @Override public void run() {
      VertxHttpServerAdapter.setCurrentMethodAndPath("GET", null);
      adapter.methodFromResponse(null);
    }
  }

  /** In JRE 1.8, mockito crashes with 'Mockito cannot mock this class' */
  HttpServerResponse dummyResponse() {
    return (HttpServerResponse) Proxy.newProxyInstance(
        getClass().getClassLoader(),
        new Class[] {HttpServerResponse.class},
        (proxy, method, methodArgs) -> {
          throw new UnsupportedOperationException(
              "Unsupported method: " + method.getName());
        });
  }
}

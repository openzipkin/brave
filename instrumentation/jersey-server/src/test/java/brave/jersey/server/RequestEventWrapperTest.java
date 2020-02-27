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
package brave.jersey.server;

import brave.jersey.server.TracingApplicationEventListener.RequestEventWrapper;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RequestEventWrapperTest {
  @Mock ContainerRequest request;
  @Mock RequestEvent event;
  @Mock ContainerResponse response;

  @Test public void method() {
    when(event.getContainerRequest()).thenReturn(request);
    when(request.getMethod()).thenReturn("GET");

    assertThat(new RequestEventWrapper(event).method())
      .isEqualTo("GET");
  }

  @Test public void request() {
    when(event.getContainerRequest()).thenReturn(request);

    assertThat(new RequestEventWrapper(event).request().unwrap())
      .isSameAs(request);
  }

  @Test public void statusCode() {
    when(event.getContainerResponse()).thenReturn(response);
    when(response.getStatus()).thenReturn(200);

    assertThat(new RequestEventWrapper(event).statusCode()).isEqualTo(200);
  }

  @Test public void statusCode_zeroNoResponse() {
    assertThat(new RequestEventWrapper(event).statusCode()).isZero();
  }
}

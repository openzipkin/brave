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
package brave.jaxrs2;

import brave.jaxrs2.TracingClientFilter.ClientRequestContextWrapper;
import java.net.URI;
import java.util.Collections;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.core.MultivaluedMap;
import org.jboss.resteasy.core.Headers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ClientRequestContextWrapperTest {
  @Mock ClientRequestContext request;

  @Test void method() {
    when(request.getMethod()).thenReturn("GET");

    assertThat(new ClientRequestContextWrapper(request).method()).isEqualTo("GET");
  }

  @Test void path() {
    when(request.getUri()).thenReturn(URI.create("http://localhost/api"));

    assertThat(new ClientRequestContextWrapper(request).path()).isEqualTo("/api");
  }

  // NOTE: While technically possible, it is not easy to make URI.getPath() return null!
  @Test void path_emptyToSlash() {
    when(request.getUri()).thenReturn(URI.create("http://localhost"));

    assertThat(new ClientRequestContextWrapper(request).path())
      .isEqualTo("/");
  }

  @Test void url() {
    when(request.getUri()).thenReturn(URI.create("http://localhost/api"));

    assertThat(new ClientRequestContextWrapper(request).url()).isEqualTo("http://localhost/api");
  }

  @Test void header() {
    when(request.getHeaderString("name")).thenReturn("value");

    assertThat(new ClientRequestContextWrapper(request).header("name")).isEqualTo("value");
  }

  @Test void putHeader() {
    MultivaluedMap<String, Object> headers = new Headers<>();
    when(request.getHeaders()).thenReturn(headers);

    new ClientRequestContextWrapper(request).header("name", "value");

    assertThat(headers).containsExactly(entry("name", Collections.singletonList("value")));
  }
}

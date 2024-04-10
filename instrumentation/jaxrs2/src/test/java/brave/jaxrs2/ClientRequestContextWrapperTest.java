/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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

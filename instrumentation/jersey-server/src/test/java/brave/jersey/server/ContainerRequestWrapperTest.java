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

import brave.jersey.server.TracingApplicationEventListener.ContainerRequestWrapper;
import java.net.URI;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ContainerRequestWrapperTest {
  ContainerRequest request = mock(ContainerRequest.class);

  @Test public void path_prefixesSlashWhenMissing() {
    when(request.getPath(false)).thenReturn("bar");

    assertThat(new ContainerRequestWrapper(request).path())
      .isEqualTo("/bar");
  }

  @Test public void url_derivedFromExtendedUriInfo() {
    ExtendedUriInfo uriInfo = mock(ExtendedUriInfo.class);
    when(request.getUriInfo()).thenReturn(uriInfo);
    when(uriInfo.getRequestUri()).thenReturn(URI.create("http://foo:8080/bar?hello=world"));

    assertThat(new ContainerRequestWrapper(request).url())
      .isEqualTo("http://foo:8080/bar?hello=world");
  }
}

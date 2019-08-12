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
package brave.jersey.server;

import java.net.URI;
import java.util.Arrays;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.uri.PathTemplate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SpanCustomizingApplicationEventListenerTest {
  @Mock ContainerRequest request;

  @Test public void route() {
    ExtendedUriInfo uriInfo = mock(ExtendedUriInfo.class);
    when(request.getUriInfo()).thenReturn(uriInfo);
    when(uriInfo.getBaseUri()).thenReturn(URI.create("/"));
    when(uriInfo.getMatchedTemplates()).thenReturn(Arrays.asList(
      new PathTemplate("/"),
      new PathTemplate("/items/{itemId}")
    ));

    assertThat(SpanCustomizingApplicationEventListener.route(request))
      .isEqualTo("/items/{itemId}");
  }

  @Test public void route_noPath() {
    ExtendedUriInfo uriInfo = mock(ExtendedUriInfo.class);
    when(request.getUriInfo()).thenReturn(uriInfo);
    when(uriInfo.getBaseUri()).thenReturn(URI.create("/"));
    when(uriInfo.getMatchedTemplates()).thenReturn(Arrays.asList(
      new PathTemplate("/eggs")
    ));

    assertThat(SpanCustomizingApplicationEventListener.route(request))
      .isEqualTo("/eggs");
  }

  /** not sure it is even possible for a template to match "/" "/".. */
  @Test public void route_invalid() {
    ExtendedUriInfo uriInfo = mock(ExtendedUriInfo.class);
    when(request.getUriInfo()).thenReturn(uriInfo);
    when(uriInfo.getBaseUri()).thenReturn(URI.create("/"));
    when(uriInfo.getMatchedTemplates()).thenReturn(Arrays.asList(
      new PathTemplate("/"),
      new PathTemplate("/")
    ));

    assertThat(SpanCustomizingApplicationEventListener.route(request))
      .isEmpty();
  }

  @Test public void route_basePath() {
    ExtendedUriInfo uriInfo = mock(ExtendedUriInfo.class);
    when(request.getUriInfo()).thenReturn(uriInfo);
    when(uriInfo.getBaseUri()).thenReturn(URI.create("/base"));
    when(uriInfo.getMatchedTemplates()).thenReturn(Arrays.asList(
      new PathTemplate("/"),
      new PathTemplate("/items/{itemId}")
    ));

    assertThat(SpanCustomizingApplicationEventListener.route(request))
      .isEqualTo("/base/items/{itemId}");
  }

  @Test public void route_nested() {
    ExtendedUriInfo uriInfo = mock(ExtendedUriInfo.class);
    when(request.getUriInfo()).thenReturn(uriInfo);
    when(uriInfo.getBaseUri()).thenReturn(URI.create("/"));
    when(uriInfo.getMatchedTemplates()).thenReturn(Arrays.asList(
      new PathTemplate("/"),
      new PathTemplate("/items/{itemId}"),
      new PathTemplate("/"),
      new PathTemplate("/nested")
    ));

    assertThat(SpanCustomizingApplicationEventListener.route(request))
      .isEqualTo("/nested/items/{itemId}");
  }

  /** when the path expression is on the type not on the method */
  @Test public void route_nested_reverse() {
    ExtendedUriInfo uriInfo = mock(ExtendedUriInfo.class);
    when(request.getUriInfo()).thenReturn(uriInfo);
    when(uriInfo.getBaseUri()).thenReturn(URI.create("/"));
    when(uriInfo.getMatchedTemplates()).thenReturn(Arrays.asList(
      new PathTemplate("/items/{itemId}"),
      new PathTemplate("/"),
      new PathTemplate("/nested"),
      new PathTemplate("/")
    ));

    assertThat(SpanCustomizingApplicationEventListener.route(request))
      .isEqualTo("/nested/items/{itemId}");
  }
}

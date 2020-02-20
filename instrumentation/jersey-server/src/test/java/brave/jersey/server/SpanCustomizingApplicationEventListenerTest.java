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

import brave.SpanCustomizer;
import java.net.URI;
import java.util.Arrays;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.uri.PathTemplate;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SpanCustomizingApplicationEventListenerTest {
  @Mock EventParser parser;
  @Mock RequestEvent requestEvent;
  @Mock ContainerRequest request;
  @Mock ExtendedUriInfo uriInfo;
  @Mock SpanCustomizer span;
  SpanCustomizingApplicationEventListener listener;

  @Before public void setup() {
    listener = SpanCustomizingApplicationEventListener.create(parser);
    when(requestEvent.getContainerRequest()).thenReturn(request);
    when(request.getUriInfo()).thenReturn(uriInfo);
  }

  @Test public void onEvent_processesFINISHED() {
    setEventType(RequestEvent.Type.FINISHED);
    setBaseUri("/");

    when(request.getProperty(SpanCustomizer.class.getName())).thenReturn(span);

    listener.onEvent(requestEvent);

    verify(parser).requestMatched(requestEvent, span);
  }

  @Test public void onEvent_setsErrorWhenNotAlreadySet() {
    setEventType(RequestEvent.Type.FINISHED);
    setBaseUri("/");

    when(request.getProperty(SpanCustomizer.class.getName())).thenReturn(span);

    Exception error = new Exception();
    when(requestEvent.getException()).thenReturn(error);
    when(request.getProperty("error")).thenReturn(null);

    listener.onEvent(requestEvent);

    verify(request).setProperty("error", error);
  }

  /** Don't clobber user-defined properties! */
  @Test public void onEvent_skipsErrorWhenSet() {
    setEventType(RequestEvent.Type.FINISHED);
    setBaseUri("/");

    when(request.getProperty(SpanCustomizer.class.getName())).thenReturn(span);

    Exception error = new Exception();
    when(requestEvent.getException()).thenReturn(error);
    when(request.getProperty("error")).thenReturn("madness");

    listener.onEvent(requestEvent);

    verify(request).getProperty(SpanCustomizer.class.getName());
    verify(request).getProperty("error");
    verify(request).getUriInfo();
    verify(request).setProperty("http.route", ""); // empty means no route found
    verifyNoMoreInteractions(request); // no setting of error
  }

  @Test public void onEvent_toleratesMissingCustomizer() {
    setEventType(RequestEvent.Type.FINISHED);
    setBaseUri("/");

    listener.onEvent(requestEvent);

    verifyNoMoreInteractions(parser);
  }

  @Test public void onEvent_toleratesBadCustomizer() {
    setEventType(RequestEvent.Type.FINISHED);
    setBaseUri("/");

    when(request.getProperty(SpanCustomizer.class.getName())).thenReturn("eyeballs");

    listener.onEvent(requestEvent);

    verifyNoMoreInteractions(parser);
  }

  @Test public void onEvent_ignoresNotFinished() {
    for (RequestEvent.Type type : RequestEvent.Type.values()) {
      if (type == RequestEvent.Type.FINISHED) return;

      setEventType(type);

      listener.onEvent(requestEvent);

      verifyNoMoreInteractions(span);
    }
  }

  @Test public void ignoresEventsExceptFinish() {
    setBaseUri("/");
    when(uriInfo.getMatchedTemplates()).thenReturn(Arrays.asList(
      new PathTemplate("/"),
      new PathTemplate("/items/{itemId}")
    ));

    assertThat(SpanCustomizingApplicationEventListener.route(request))
      .isEqualTo("/items/{itemId}");
  }

  @Test public void route() {
    setBaseUri("/");
    when(uriInfo.getMatchedTemplates()).thenReturn(Arrays.asList(
      new PathTemplate("/"),
      new PathTemplate("/items/{itemId}")
    ));

    assertThat(SpanCustomizingApplicationEventListener.route(request))
      .isEqualTo("/items/{itemId}");
  }

  @Test public void route_noPath() {
    setBaseUri("/");
    when(uriInfo.getMatchedTemplates()).thenReturn(Arrays.asList(
      new PathTemplate("/eggs")
    ));

    assertThat(SpanCustomizingApplicationEventListener.route(request))
      .isEqualTo("/eggs");
  }

  /** not sure it is even possible for a template to match "/" "/".. */
  @Test public void route_invalid() {
    setBaseUri("/");
    when(uriInfo.getMatchedTemplates()).thenReturn(Arrays.asList(
      new PathTemplate("/"),
      new PathTemplate("/")
    ));

    assertThat(SpanCustomizingApplicationEventListener.route(request))
      .isEmpty();
  }

  @Test public void route_basePath() {
    setBaseUri("/base");
    when(uriInfo.getMatchedTemplates()).thenReturn(Arrays.asList(
      new PathTemplate("/"),
      new PathTemplate("/items/{itemId}")
    ));

    assertThat(SpanCustomizingApplicationEventListener.route(request))
      .isEqualTo("/base/items/{itemId}");
  }

  @Test public void route_nested() {
    setBaseUri("/");
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
    setBaseUri("/");
    when(uriInfo.getMatchedTemplates()).thenReturn(Arrays.asList(
      new PathTemplate("/items/{itemId}"),
      new PathTemplate("/"),
      new PathTemplate("/nested"),
      new PathTemplate("/")
    ));

    assertThat(SpanCustomizingApplicationEventListener.route(request))
      .isEqualTo("/nested/items/{itemId}");
  }

  void setBaseUri(String path) {
    when(uriInfo.getBaseUri()).thenReturn(URI.create(path));
  }

  void setEventType(RequestEvent.Type type) {
    when(requestEvent.getType()).thenReturn(type);
  }
}

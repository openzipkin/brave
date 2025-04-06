/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jakarta.jersey.server;

import brave.SpanCustomizer;
import java.net.URI;
import java.util.Arrays;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.uri.PathTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // TODO: hunt down these
public class SpanCustomizingApplicationEventListenerTest {
  @Mock
  EventParser parser;
  @Mock RequestEvent requestEvent;
  @Mock ContainerRequest request;
  @Mock ExtendedUriInfo uriInfo;
  @Mock SpanCustomizer span;
  SpanCustomizingApplicationEventListener listener;

  @BeforeEach void setup() {
    listener = SpanCustomizingApplicationEventListener.create(parser);
    when(requestEvent.getContainerRequest()).thenReturn(request);
    when(request.getUriInfo()).thenReturn(uriInfo);
  }

  @Test void onEvent_processesFINISHED() {
    setEventType(RequestEvent.Type.FINISHED);
    setBaseUri("/");

    when(request.getProperty(SpanCustomizer.class.getName())).thenReturn(span);

    listener.onEvent(requestEvent);

    verify(parser).requestMatched(requestEvent, span);
  }

  @Test void onEvent_setsErrorWhenNotAlreadySet() {
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
  @Test void onEvent_skipsErrorWhenSet() {
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

  @Test void onEvent_toleratesMissingCustomizer() {
    setEventType(RequestEvent.Type.FINISHED);
    setBaseUri("/");

    listener.onEvent(requestEvent);

    verifyNoMoreInteractions(parser);
  }

  @Test void onEvent_toleratesBadCustomizer() {
    setEventType(RequestEvent.Type.FINISHED);
    setBaseUri("/");

    when(request.getProperty(SpanCustomizer.class.getName())).thenReturn("eyeballs");

    listener.onEvent(requestEvent);

    verifyNoMoreInteractions(parser);
  }

  @Test void onEvent_ignoresNotFinished() {
    for (RequestEvent.Type type : RequestEvent.Type.values()) {
      if (type == RequestEvent.Type.FINISHED) return;

      setEventType(type);

      listener.onEvent(requestEvent);

      verifyNoMoreInteractions(span);
    }
  }

  @Test void ignoresEventsExceptFinish() {
    setBaseUri("/");
    when(uriInfo.getMatchedTemplates()).thenReturn(Arrays.asList(
      new PathTemplate("/"),
      new PathTemplate("/items/{itemId}")
    ));

    assertThat(SpanCustomizingApplicationEventListener.route(request))
      .isEqualTo("/items/{itemId}");
  }

  @Test void route() {
    setBaseUri("/");
    when(uriInfo.getMatchedTemplates()).thenReturn(Arrays.asList(
      new PathTemplate("/"),
      new PathTemplate("/items/{itemId}")
    ));

    assertThat(SpanCustomizingApplicationEventListener.route(request))
      .isEqualTo("/items/{itemId}");
  }

  @Test void route_noPath() {
    setBaseUri("/");
    when(uriInfo.getMatchedTemplates()).thenReturn(Arrays.asList(
      new PathTemplate("/eggs")
    ));

    assertThat(SpanCustomizingApplicationEventListener.route(request))
      .isEqualTo("/eggs");
  }

  /** not sure it is even possible for a template to match "/" "/".. */
  @Test void route_invalid() {
    setBaseUri("/");
    when(uriInfo.getMatchedTemplates()).thenReturn(Arrays.asList(
      new PathTemplate("/"),
      new PathTemplate("/")
    ));

    assertThat(SpanCustomizingApplicationEventListener.route(request))
      .isEmpty();
  }

  @Test void route_basePath() {
    setBaseUri("/base");
    when(uriInfo.getMatchedTemplates()).thenReturn(Arrays.asList(
      new PathTemplate("/"),
      new PathTemplate("/items/{itemId}")
    ));

    assertThat(SpanCustomizingApplicationEventListener.route(request))
      .isEqualTo("/base/items/{itemId}");
  }

  @Test void route_nested() {
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
  @Test void route_nested_reverse() {
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

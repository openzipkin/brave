/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jersey.server;

import brave.SpanCustomizer;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class EventParserTest {
  @Mock RequestEvent event;
  @Mock ContainerRequest request;
  @Mock ExtendedUriInfo uriInfo;
  @Mock SpanCustomizer customizer;

  EventParser eventParser = new EventParser();

  @Test void requestMatched_missingResourceMethodOk() {
    when(event.getContainerRequest()).thenReturn(request);
    when(request.getUriInfo()).thenReturn(uriInfo);

    eventParser.requestMatched(event, customizer);

    verifyNoMoreInteractions(customizer);
  }
}

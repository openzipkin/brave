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

import brave.SpanCustomizer;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class EventParserTest {
  @Mock RequestEvent event;
  @Mock ContainerRequest request;
  @Mock ExtendedUriInfo uriInfo;
  @Mock SpanCustomizer customizer;

  EventParser eventParser = new EventParser();

  @Test public void requestMatched_missingResourceMethodOk() {
    when(event.getContainerRequest()).thenReturn(request);
    when(request.getUriInfo()).thenReturn(uriInfo);

    eventParser.requestMatched(event, customizer);

    verifyNoMoreInteractions(customizer);
  }
}

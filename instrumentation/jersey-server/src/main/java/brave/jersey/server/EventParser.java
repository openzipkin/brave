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
import brave.http.HttpTracing;
import org.glassfish.jersey.server.model.Invocable;
import org.glassfish.jersey.server.model.ResourceMethod;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;

/**
 * Jersey specific type used to customize traced requests based on the JAX-RS resource.
 *
 * <p>Note: This should not duplicate data added by {@link HttpTracing}. For example, this should
 * not add the tag "http.url".
 */
// named event parser, not request event parser, in case we want to later support application event.
public class EventParser {
  /** Adds no data to the request */
  public static final EventParser NOOP = new EventParser() {
    @Override protected void requestMatched(RequestEvent event, SpanCustomizer customizer) {
    }
  };

  /** Simple class name that processed the request. ex BookResource */
  public static final String RESOURCE_CLASS = "jaxrs.resource.class";
  /** Method name that processed the request. ex listOfBooks */
  public static final String RESOURCE_METHOD = "jaxrs.resource.method";

  /**
   * Invoked prior to request invocation during {@link RequestEventListener#onEvent(RequestEvent)}
   * where the event type is {@link RequestEvent.Type#REQUEST_MATCHED}
   *
   * <p>Adds the tags {@link #RESOURCE_CLASS} and {@link #RESOURCE_METHOD}. Override or use {@link
   * #NOOP}
   * to change this behavior.
   */
  protected void requestMatched(RequestEvent event, SpanCustomizer customizer) {
    ResourceMethod method = event.getContainerRequest().getUriInfo().getMatchedResourceMethod();
    if (method == null) return; // This case is extremely odd as this is called on REQUEST_MATCHED!
    Invocable i = method.getInvocable();
    customizer.tag(RESOURCE_CLASS, i.getHandler().getHandlerClass().getSimpleName());
    customizer.tag(RESOURCE_METHOD, i.getHandlingMethod().getName());
  }

  public EventParser() { // intentionally public for @Inject to work without explicit binding
  }
}

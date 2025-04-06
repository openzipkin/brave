/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jakarta.jersey.server;

import brave.SpanCustomizer;
import brave.internal.Nullable;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.ext.Provider;
import java.util.List;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.internal.process.MappableException;
import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;
import org.glassfish.jersey.uri.UriTemplate;

import static org.glassfish.jersey.server.monitoring.RequestEvent.Type.FINISHED;

/**
 * Adds application-tier data to an existing http span via {@link EventParser}. This also sets the
 * request property "http.route" so that it can be used in naming the http span.
 *
 * <p>Use this instead of {@link TracingApplicationEventListener} when you start traces at the
 * servlet level via {@code brave.servlet.TracingFilter}.
 */
@Provider
public class SpanCustomizingApplicationEventListener
  implements ApplicationEventListener, RequestEventListener {
  public static SpanCustomizingApplicationEventListener create() {
    return new SpanCustomizingApplicationEventListener(new EventParser());
  }

  public static SpanCustomizingApplicationEventListener create(EventParser parser) {
    return new SpanCustomizingApplicationEventListener(parser);
  }

  final EventParser parser;

  @Inject
  SpanCustomizingApplicationEventListener(EventParser parser) {
    if (parser == null) throw new NullPointerException("parser == null");
    this.parser = parser;
  }

  @Override public void onEvent(ApplicationEvent event) {
    // only onRequest is used
  }

  @Override public RequestEventListener onRequest(RequestEvent requestEvent) {
    if (requestEvent.getType() == RequestEvent.Type.START) return this;
    return null;
  }

  @Override public void onEvent(RequestEvent event) {
    // Note: until REQUEST_MATCHED, we don't know metadata such as if the request is async or not
    if (event.getType() != FINISHED) return;
    ContainerRequest request = event.getContainerRequest();
    Object maybeSpan = request.getProperty(SpanCustomizer.class.getName());
    if (!(maybeSpan instanceof SpanCustomizer)) return;

    // Set the HTTP route attribute so that TracingFilter can see it
    request.setProperty("http.route", route(request));

    Throwable error = unwrapError(event);
    // Set the error attribute so that TracingFilter can see it
    if (error != null && request.getProperty("error") == null) request.setProperty("error", error);

    parser.requestMatched(event, (SpanCustomizer) maybeSpan);
  }

  @Nullable static Throwable unwrapError(RequestEvent event) {
    Throwable error = event.getException();
    // For example, if thrown in an async controller
    if (error instanceof MappableException && error.getCause() != null) {
      error = error.getCause();
    }
    // Don't create error messages for normal HTTP status codes.
    if (error instanceof WebApplicationException) return error.getCause();
    return error;
  }

  /**
   * This returns the matched template as defined by a base URL and path expressions.
   *
   * <p>Matched templates are pairs of (resource path, method path) added with
   * {@link org.glassfish.jersey.server.internal.routing.RoutingContext#pushTemplates(UriTemplate,
   * UriTemplate)}. This code skips redundant slashes from either source caused by Path("/") or
   * Path("").
   */
  @Nullable static String route(ContainerRequest request) {
    ExtendedUriInfo uriInfo = request.getUriInfo();
    List<UriTemplate> templates = uriInfo.getMatchedTemplates();
    int templateCount = templates.size();
    if (templateCount == 0) return "";
    StringBuilder builder = null; // don't allocate unless you need it!
    String basePath = uriInfo.getBaseUri().getPath();
    String result = null;
    if (!"/" .equals(basePath)) { // skip empty base paths
      result = basePath;
    }
    for (int i = templateCount - 1; i >= 0; i--) {
      String template = templates.get(i).getTemplate();
      if ("/" .equals(template)) continue; // skip allocation
      if (builder != null) {
        builder.append(template);
      } else if (result != null) {
        builder = new StringBuilder(result).append(template);
        result = null;
      } else {
        result = template;
      }
    }
    return result != null ? result : builder != null ? builder.toString() : "";
  }
}

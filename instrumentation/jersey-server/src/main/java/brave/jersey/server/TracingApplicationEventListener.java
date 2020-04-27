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

import brave.Span;
import brave.http.HttpServerHandler;
import brave.http.HttpServerRequest;
import brave.http.HttpServerResponse;
import brave.http.HttpTracing;
import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.ext.Provider;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.ManagedAsync;
import org.glassfish.jersey.server.internal.process.MappableException;
import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;

@Provider
public final class TracingApplicationEventListener implements ApplicationEventListener {
  public static ApplicationEventListener create(HttpTracing httpTracing) {
    return new TracingApplicationEventListener(httpTracing, new EventParser());
  }

  final CurrentTraceContext currentTraceContext;
  final HttpServerHandler<HttpServerRequest, HttpServerResponse> handler;
  final EventParser parser;

  @Inject TracingApplicationEventListener(HttpTracing httpTracing, EventParser parser) {
    currentTraceContext = httpTracing.tracing().currentTraceContext();
    handler = HttpServerHandler.create(httpTracing);
    this.parser = parser;
  }

  @Override public void onEvent(ApplicationEvent event) {
    // only onRequest is used
  }

  @Override public RequestEventListener onRequest(RequestEvent event) {
    if (event.getType() != RequestEvent.Type.START) return null;
    Span span = handler.handleReceive(new ContainerRequestWrapper(event.getContainerRequest()));
    return new TracingRequestEventListener(span, currentTraceContext.newScope(span.context()));
  }

  // Scope reference invalidated when an asynchronous method is in use
  class TracingRequestEventListener extends AtomicReference<Scope> implements RequestEventListener {
    final Span span;
    volatile boolean async;

    TracingRequestEventListener(Span span, Scope scope) {
      super(scope);
      this.span = span;
    }

    /**
     * This keeps the span in scope as long as possible. In synchronous methods, the span remains in
     * scope for the whole request/response lifecycle. {@linkplain ManagedAsync} and {@linkplain
     * Suspended} requests are the worst case: the span is only visible until request filters
     * complete.
     */
    @Override
    public void onEvent(RequestEvent event) {
      Scope maybeScope;
      switch (event.getType()) {
        // Note: until REQUEST_MATCHED, we don't know metadata such as if the request is async or not
        case REQUEST_MATCHED:
          parser.requestMatched(event, span);
          async = async(event);
          break;
        case REQUEST_FILTERED:
        case RESOURCE_METHOD_FINISHED:
          // If we scoped above, we have to close that to avoid leaks.
          // Jersey-specific @ManagedAsync stays on the request thread until REQUEST_FILTERED
          // Normal async methods sometimes stay on a thread until RESOURCE_METHOD_FINISHED, but
          // this is not reliable. So, we eagerly close the scope from request filters, and re-apply
          // it later when the resource method starts.
          if (!async || (maybeScope = getAndSet(null)) == null) break;
          maybeScope.close();
          break;
        case RESOURCE_METHOD_START:
          // If we are async, we have to re-scope the span as the resource method invocation is
          // is likely on a different thread than the request filtering.
          if (!async || get() != null) break;
          set(currentTraceContext.newScope(span.context()));
          break;
        case FINISHED:
          handler.handleSend(new RequestEventWrapper(event), span);
          // In async FINISHED can happen before RESOURCE_METHOD_FINISHED, and on different threads!
          // Don't close the scope unless it is a synchronous method.
          if (!async && (maybeScope = getAndSet(null)) != null) {
            maybeScope.close();
          }
          break;
        default:
      }
    }
  }

  static boolean async(RequestEvent event) {
    return event.getUriInfo().getMatchedResourceMethod().isManagedAsyncDeclared()
      || event.getUriInfo().getMatchedResourceMethod().isSuspendDeclared();
  }

  static final class ContainerRequestWrapper extends HttpServerRequest {
    final ContainerRequest delegate;

    ContainerRequestWrapper(ContainerRequest delegate) {
      this.delegate = delegate;
    }

    @Override public String route() {
      return SpanCustomizingApplicationEventListener.route(delegate);
    }

    @Override public Object unwrap() {
      return delegate;
    }

    @Override public String method() {
      return delegate.getMethod();
    }

    @Override public String path() {
      String result = delegate.getPath(false);
      return result.indexOf('/') == 0 ? result : "/" + result;
    }

    @Override public String url() {
      return delegate.getUriInfo().getRequestUri().toString();
    }

    @Override public String header(String name) {
      return delegate.getHeaderString(name);
    }

    // NOTE: this currently lacks remote socket parsing even though some platforms might work. For
    // example, org.glassfish.grizzly.http.server.Request.getRemoteAddr or
    // HttpServletRequest.getRemoteAddr
  }

  static final class RequestEventWrapper extends HttpServerResponse {
    final RequestEvent event;
    @Nullable final Throwable error;
    ContainerRequestWrapper request;

    RequestEventWrapper(RequestEvent event) {
      this.event = event;
      this.error = SpanCustomizingApplicationEventListener.unwrapError(event);
    }

    @Override public Object unwrap() {
      return event;
    }

    @Override public ContainerRequestWrapper request() {
      if (request == null) request = new ContainerRequestWrapper(event.getContainerRequest());
      return request;
    }

    @Override public Throwable error() {
      return error;
    }

    @Override public int statusCode() {
      ContainerResponse response = event.getContainerResponse();
      if (response != null) return response.getStatus();

      Throwable error = event.getException();
      // For example, if thrown in an async controller
      if (error instanceof MappableException && error.getCause() != null) {
        error = error.getCause();
      }
      if (error instanceof WebApplicationException) {
        return ((WebApplicationException) error).getResponse().getStatus();
      }
      return 0;
    }
  }
}

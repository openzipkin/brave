package brave.jersey.server;

import brave.Span;
import brave.Tracer;
import brave.http.HttpServerAdapter;
import brave.http.HttpServerHandler;
import brave.http.HttpTracing;
import brave.propagation.Propagation.Getter;
import brave.propagation.TraceContext;
import javax.inject.Inject;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.ext.Provider;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.ManagedAsync;
import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;

import static brave.jersey.server.SpanCustomizingApplicationEventListener.route;

@Provider
public final class TracingApplicationEventListener implements ApplicationEventListener {
  public static ApplicationEventListener create(HttpTracing httpTracing) {
    return new TracingApplicationEventListener(httpTracing, new EventParser());
  }

  static final Getter<ContainerRequest, String> GETTER = new Getter<ContainerRequest, String>() {
    @Override public String get(ContainerRequest carrier, String key) {
      return carrier.getHeaderString(key);
    }

    @Override public String toString() {
      return "ContainerRequest::getHeaderString";
    }
  };

  final Tracer tracer;
  final HttpServerHandler<ContainerRequest, RequestEvent> serverHandler;
  final TraceContext.Extractor<ContainerRequest> extractor;
  final EventParser parser;

  @Inject TracingApplicationEventListener(HttpTracing httpTracing, EventParser parser) {
    tracer = httpTracing.tracing().tracer();
    serverHandler = HttpServerHandler.create(httpTracing, new Adapter());
    extractor = httpTracing.tracing().propagation().extractor(GETTER);
    this.parser = parser;
  }

  @Override public void onEvent(ApplicationEvent event) {
    // only onRequest is used
  }

  @Override
  public RequestEventListener onRequest(RequestEvent requestEvent) {
    if (requestEvent.getType() == RequestEvent.Type.START) {
      Span span = serverHandler.handleReceive(extractor, requestEvent.getContainerRequest());
      return new TracingRequestEventListener(span, tracer.withSpanInScope(span));
    }
    return null;
  }

  static final class Adapter extends HttpServerAdapter<ContainerRequest, RequestEvent> {

    @Override public String method(ContainerRequest request) {
      return request.getMethod();
    }

    @Override public String path(ContainerRequest request) {
      String result = request.getPath(false);
      return result.indexOf('/') == 0 ? result : "/" + result;
    }

    @Override public String url(ContainerRequest request) {
      return request.getUriInfo().getRequestUri().toString();
    }

    @Override public String requestHeader(ContainerRequest request, String name) {
      return request.getHeaderString(name);
    }

    @Override public String methodFromResponse(RequestEvent event) {
      return event.getContainerRequest().getMethod();
    }

    @Override public String route(RequestEvent event) {
      return (String) event.getContainerRequest().getProperty("http.route");
    }

    @Override public Integer statusCode(RequestEvent event) {
      return statusCodeAsInt(event);
    }

    @Override public int statusCodeAsInt(RequestEvent event) {
      ContainerResponse response = event.getContainerResponse();
      if (response == null) return 0;
      return response.getStatus();
    }

    // NOTE: this currently lacks remote socket parsing even though some platforms might work. For
    // example, org.glassfish.grizzly.http.server.Request.getRemoteAddr or
    // HttpServletRequest.getRemoteAddr
  }

  class TracingRequestEventListener implements RequestEventListener {
    final Span span;
    // Invalidated when an asynchronous method is in use
    volatile Tracer.SpanInScope spanInScope;
    volatile boolean async;

    TracingRequestEventListener(Span span, Tracer.SpanInScope spanInScope) {
      this.span = span;
      this.spanInScope = spanInScope;
    }

    /**
     * This keeps the span in scope as long as possible. In synchronous methods, the span remains in
     * scope for the whole request/response lifecycle. {@linkplain ManagedAsync} and {@linkplain
     * Suspended} requests are the worst case: the span is only visible until request filters
     * complete.
     */
    @Override
    public void onEvent(RequestEvent event) {
      Tracer.SpanInScope maybeSpanInScope;
      switch (event.getType()) {
        // Note: until REQUEST_MATCHED, we don't know metadata such as if the request is async or not
        case REQUEST_MATCHED:
          parser.requestMatched(event, span);
          async = async(event);
          break;
        case REQUEST_FILTERED:
          // Jersey-specific @ManagedAsync stays on the request thread until REQUEST_FILTERED
          // Normal async methods sometimes stay on a thread until RESOURCE_METHOD_FINISHED, but
          // this is not reliable. So, we eagerly close the scope from request filters, and re-apply
          // it later when the resource method starts.
          if (!async || (maybeSpanInScope = spanInScope) == null) break;
          maybeSpanInScope.close();
          spanInScope = null;
          break;
        case RESOURCE_METHOD_START:
          // If we are async, we have to re-scope the span as the resource method invocation is
          // is likely on a different thread than the request filtering.
          if (!async || spanInScope != null) break;
          spanInScope = tracer.withSpanInScope(span);
          break;
        case RESOURCE_METHOD_FINISHED:
          // If we scoped above, we have to close that to avoid leaks.
          if (!async || (maybeSpanInScope = spanInScope) == null) break;
          maybeSpanInScope.close();
          spanInScope = null;
          break;
        case FINISHED:
          // In async FINISHED can happen before RESOURCE_METHOD_FINISHED, and on different threads!
          // Don't close the scope unless it is a synchronous method.
          if (!async && (maybeSpanInScope = spanInScope) != null) {
            maybeSpanInScope.close();
          }
          String maybeHttpRoute = route(event.getContainerRequest());
          if (maybeHttpRoute != null) {
            event.getContainerRequest().setProperty("http.route", maybeHttpRoute);
          }
          serverHandler.handleSend(event, event.getException(), span);
          break;
        default:
      }
    }
  }

  static boolean async(RequestEvent event) {
    return event.getUriInfo().getMatchedResourceMethod().isManagedAsyncDeclared()
        || event.getUriInfo().getMatchedResourceMethod().isSuspendDeclared();
  }
}

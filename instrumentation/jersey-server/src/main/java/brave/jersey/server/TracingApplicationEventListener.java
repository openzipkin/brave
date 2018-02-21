package brave.jersey.server;

import brave.Span;
import brave.Tracer;
import brave.http.HttpServerAdapter;
import brave.http.HttpServerHandler;
import brave.http.HttpTracing;
import brave.propagation.Propagation.Getter;
import brave.propagation.TraceContext;
import javax.inject.Inject;
import javax.ws.rs.ext.Provider;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.ManagedAsync;
import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;

@Provider
public final class TracingApplicationEventListener implements ApplicationEventListener {
  static final Getter<ContainerRequest, String> GETTER = new Getter<ContainerRequest, String>() {
    @Override public String get(ContainerRequest carrier, String key) {
      return carrier.getHeaderString(key);
    }

    @Override public String toString() {
      return "ContainerRequest::getHeaderString";
    }
  };

  final Tracer tracer;
  final HttpServerHandler<ContainerRequest, ContainerResponse> serverHandler;
  final TraceContext.Extractor<ContainerRequest> extractor;

  @Inject TracingApplicationEventListener(HttpTracing httpTracing) {
    tracer = httpTracing.tracing().tracer();
    serverHandler = HttpServerHandler.create(httpTracing, new Adapter());
    extractor = httpTracing.tracing().propagation().extractor(GETTER);
  }

  public static ApplicationEventListener create(HttpTracing httpTracing) {
    return new TracingApplicationEventListener(httpTracing);
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

  static final class Adapter extends HttpServerAdapter<ContainerRequest, ContainerResponse> {

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

    @Override public Integer statusCode(ContainerResponse response) {
      return response.getStatus();
    }

    // NOTE: this currently lacks remote socket parsing eventhough some platforms might work. For
    // example, org.glassfish.grizzly.http.server.Request.getRemoteAddr or
    // HttpServletRequest.getRemoteAddr
  }

  class TracingRequestEventListener implements RequestEventListener {
    final Span span;
    Tracer.SpanInScope spanInScope; // only mutated when this is a synchronous method

    TracingRequestEventListener(Span span, Tracer.SpanInScope spanInScope) {
      this.span = span;
      this.spanInScope = spanInScope;
    }

    /**
     * This keeps the span in scope as long as possible. In synchronous methods, the span remains
     * in scope for the whole request/response lifecycle. {@linkplain ManagedAsync} requests are the
     * worst case: the span is only visible until request filters complete.
     */
    @Override public void onEvent(RequestEvent event) {
      // Note: until REQUEST_MATCHED, we don't know metadata such as if the request is async or not
      switch (event.getType()) {
        case REQUEST_FILTERED:
          if (spanInScope == null) break;
          // Jersey-specific @ManagedAsync stays on the request thread until REQUEST_FILTERED
          if (event.getUriInfo().getMatchedResourceMethod().isManagedAsyncDeclared()) {
            spanInScope.close();
            spanInScope = null;
          }
          break;
        case RESOURCE_METHOD_FINISHED:
          if (spanInScope == null) break;
          // A generic async method stays on the request thread until RESOURCE_METHOD_FINISHED
          if (event.getUriInfo().getMatchedResourceMethod().isSuspendDeclared()) {
            spanInScope.close();
            spanInScope = null;
          }
          break;
        case FINISHED:
          if (spanInScope != null) spanInScope.close();
          serverHandler.handleSend(event.getContainerResponse(), event.getException(), span);
          break;
        default:
      }
    }
  }
}

package brave.jaxrs2;

import brave.Span;
import brave.Tracer;
import brave.Tracer.SpanInScope;
import brave.http.HttpServerHandler;
import brave.http.HttpTracing;
import brave.propagation.TraceContext;
import java.lang.annotation.Annotation;
import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import zipkin.Constants;
import zipkin.Endpoint;

import static javax.ws.rs.RuntimeType.SERVER;

// Currently not using PreMatching because we are attempting to detect if the method is async or not
@Provider
@Priority(0) // to make the span in scope visible to other filters
@ConstrainedTo(SERVER) final class TracingContainerFilter
    implements ContainerRequestFilter, ContainerResponseFilter {

  final Tracer tracer;
  final HttpServerHandler<ContainerRequestContext, ContainerResponseContext> handler;
  final TraceContext.Extractor<ContainerRequestContext> extractor;

  @Inject TracingContainerFilter(HttpTracing httpTracing) {
    tracer = httpTracing.tracing().tracer();
    handler = new HttpServerHandler<>(new HttpAdapter(), httpTracing.serverParser());
    extractor = httpTracing.tracing().propagation()
        .extractor(ContainerRequestContext::getHeaderString);
  }

  /**
   * This implementation peeks to see if the request is async or not, which means {@link
   * PreMatching} cannot be used: pre-matching doesn't inject the resource info!
   */
  @Context ResourceInfo resourceInfo;

  @Override public void filter(ContainerRequestContext context) {
    Span span = startSpan(context);
    if (shouldPutSpanInScope(resourceInfo)) {
      context.setProperty(SpanInScope.class.getName(), tracer.withSpanInScope(span));
    } else {
      context.setProperty(Span.class.getName(), span);
    }
  }

  private Span startSpan(ContainerRequestContext context) {
    Span span = tracer.nextSpan(extractor, context);
    parseClientAddress(context, span);
    handler.handleReceive(context, span);
    return span;
  }

  void parseClientAddress(ContainerRequestContext context, Span span) {
    if (span.isNoop()) return;
    Endpoint.Builder builder = Endpoint.builder().serviceName("");
    if (builder.parseIp(context.getHeaderString("X-Forwarded-For"))) {
      span.remoteEndpoint(builder.build());
    }
  }

  @Override
  public void filter(ContainerRequestContext request, ContainerResponseContext response) {
    Span span = (Span) request.getProperty(Span.class.getName());
    SpanInScope spanInScope = (SpanInScope) request.getProperty(SpanInScope.class.getName());
    if (span != null) { // asynchronous response or we couldn't figure it out
    } else if (spanInScope != null) { // synchronous response
      span = tracer.currentSpan();
      spanInScope.close();
    } else if (response.getStatus() == 404) {
      span = startSpan(request);
    } else {
      return; // unknown state
    }

    Response.StatusType statusInfo = response.getStatusInfo();
    if (statusInfo.getFamily() == Response.Status.Family.SERVER_ERROR) {
      span.tag(Constants.ERROR, statusInfo.getReasonPhrase());
    }
    handler.handleSend(response, span);
  }

  /**
   * We shouldn't put a span in scope unless we know for sure the request is not async. That's
   * because we cannot detach if from the calling thread when async is used.
   */
  // TODO: add benchmark and cache if slow
  static boolean shouldPutSpanInScope(ResourceInfo resourceInfo) {
    if (resourceInfo == null) return false;
    for (Annotation[] annotations : resourceInfo.getResourceMethod().getParameterAnnotations()) {
      for (Annotation annotation : annotations) {
        if (annotation.annotationType().equals(Suspended.class)) {
          return false;
        }
      }
    }
    return true;
  }

  static final class HttpAdapter
      extends brave.http.HttpAdapter<ContainerRequestContext, ContainerResponseContext> {
    @Override public String method(ContainerRequestContext request) {
      return request.getMethod();
    }

    @Override public String path(ContainerRequestContext request) {
      return request.getUriInfo().getRequestUri().getPath();
    }

    @Override public String url(ContainerRequestContext request) {
      return request.getUriInfo().getRequestUri().toString();
    }

    @Override public String requestHeader(ContainerRequestContext request, String name) {
      return request.getHeaderString(name);
    }

    @Override public Integer statusCode(ContainerResponseContext response) {
      return response.getStatus();
    }
  }
}

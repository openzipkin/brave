package brave.jaxrs2;

import brave.Span;
import brave.Tracer;
import brave.Tracer.SpanInScope;
import brave.http.HttpServerHandler;
import brave.http.HttpTracing;
import brave.propagation.Propagation.Getter;
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
import javax.ws.rs.ext.Provider;

import static javax.ws.rs.RuntimeType.SERVER;

// Currently not using PreMatching because we are attempting to detect if the method is async or not
@Provider
@Priority(0) // to make the span in scope visible to other filters
@ConstrainedTo(SERVER)
final class TracingContainerFilter implements ContainerRequestFilter, ContainerResponseFilter {
  static final Getter<ContainerRequestContext, String> GETTER =
      new Getter<ContainerRequestContext, String>() {

        @Override public String get(ContainerRequestContext carrier, String key) {
          return carrier.getHeaderString(key);
        }

        @Override public String toString() {
          return "ContainerRequestContext::getHeaderString";
        }
      };

  final Tracer tracer;
  final HttpServerHandler<ContainerRequestContext, ContainerResponseContext> handler;
  final TraceContext.Extractor<ContainerRequestContext> extractor;

  @Inject TracingContainerFilter(HttpTracing httpTracing) {
    tracer = httpTracing.tracing().tracer();
    handler = HttpServerHandler.create(httpTracing, new ContainerAdapter());
    extractor = httpTracing.tracing().propagation().extractor(GETTER);
  }

  /**
   * This implementation peeks to see if the request is async or not, which means {@link
   * PreMatching} cannot be used: pre-matching doesn't inject the resource info!
   */
  @Context ResourceInfo resourceInfo;

  @Override public void filter(ContainerRequestContext request) {
    if (resourceInfo != null) request.setProperty(ResourceInfo.class.getName(), resourceInfo);
    Span span = handler.handleReceive(extractor, request);
    request.removeProperty(ResourceInfo.class.getName());
    if (shouldPutSpanInScope(resourceInfo)) {
      request.setProperty(SpanInScope.class.getName(), tracer.withSpanInScope(span));
    } else {
      request.setProperty(Span.class.getName(), span);
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
      span = handler.handleReceive(extractor, request);
    } else {
      return; // unknown state
    }
    handler.handleSend(response, null, span);
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
}

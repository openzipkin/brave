package brave.jaxrs2;

import brave.Span;
import brave.Tracer;
import brave.Tracer.SpanInScope;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.propagation.TraceContext;
import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;

import static javax.ws.rs.RuntimeType.CLIENT;

/**
 * This filter is set at highest priority which means it executes before other filters. The impact
 * is that other filters can see the span created here via {@link Tracer#currentSpan()}. Another
 * impact is that the span will not see modifications to the request made by downstream filters.
 */
// If tags for the request are added on response, they might include changes made by other filters..
// However, the response callback isn't invoked on error, so this choice could be worse.
@Provider
@ConstrainedTo(CLIENT)
@Priority(0) // to make the span in scope visible to other filters
final class TracingClientFilter implements ClientRequestFilter, ClientResponseFilter {

  final Tracer tracer;
  final HttpClientHandler<ClientRequestContext, ClientResponseContext> handler;
  final TraceContext.Injector<MultivaluedMap> injector;

  @Inject TracingClientFilter(HttpTracing httpTracing) {
    if (httpTracing == null) throw new NullPointerException("HttpTracing == null");
    tracer = httpTracing.tracing().tracer();
    handler = HttpClientHandler.create(httpTracing, new HttpAdapter());
    injector = httpTracing.tracing().propagation().injector(MultivaluedMap::putSingle);
  }

  @Override
  public void filter(ClientRequestContext request) {
    Span span = handler.handleSend(injector, request.getHeaders(), request);
    request.setProperty(SpanInScope.class.getName(), tracer.withSpanInScope(span));
  }

  @Override
  public void filter(ClientRequestContext request, ClientResponseContext response) {
    Span span = tracer.currentSpan();
    if (span == null) return;
    ((Tracer.SpanInScope) request.getProperty(Tracer.SpanInScope.class.getName())).close();
    handler.handleReceive(response, null, span);
  }

  static final class HttpAdapter
      extends brave.http.HttpClientAdapter<ClientRequestContext, ClientResponseContext> {

    @Override public String method(ClientRequestContext request) {
      return request.getMethod();
    }

    @Override public String path(ClientRequestContext request) {
      return request.getUri().getPath();
    }

    @Override public String url(ClientRequestContext request) {
      return request.getUri().toString();
    }

    @Override public String requestHeader(ClientRequestContext request, String name) {
      return request.getHeaderString(name);
    }

    @Override public Integer statusCode(ClientResponseContext response) {
      return response.getStatus();
    }
  }
}

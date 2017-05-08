package brave.spring.web;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.propagation.TraceContext;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import zipkin.Endpoint;

public final class TracingClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {
  public static ClientHttpRequestInterceptor create(Tracing tracing) {
    return create(HttpTracing.create(tracing));
  }

  public static ClientHttpRequestInterceptor create(HttpTracing httpTracing) {
    return new TracingClientHttpRequestInterceptor(httpTracing);
  }

  final Tracer tracer;
  final String remoteServiceName;
  final HttpClientHandler<HttpRequest, ClientHttpResponse> handler;
  final TraceContext.Injector<HttpHeaders> injector;

  TracingClientHttpRequestInterceptor(HttpTracing httpTracing) {
    tracer = httpTracing.tracing().tracer();
    remoteServiceName = httpTracing.serverName();
    handler = HttpClientHandler.create(new HttpAdapter(), httpTracing.clientParser());
    injector = httpTracing.tracing().propagation().injector(HttpHeaders::set);
  }

  @Override public ClientHttpResponse intercept(HttpRequest request, byte[] body,
      ClientHttpRequestExecution execution) throws IOException {
    Span span = tracer.nextSpan();
    parseServerAddress(request, span);
    injector.inject(span.context(), request.getHeaders());
    handler.handleSend(request, span);

    ClientHttpResponse response = null;
    Throwable error = null;
    try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
      return response = execution.execute(request, body);
    } catch (IOException | RuntimeException e) {
      error = e;
      throw e;
    } finally {
      handler.handleReceive(response, error, span);
    }
  }

  void parseServerAddress(HttpRequest request, Span span) {
    if (span.isNoop()) return;
    URI uri = request.getURI();
    Endpoint.Builder builder = Endpoint.builder().serviceName(remoteServiceName);
    if (!builder.parseIp(uri.getHost()) && "".equals(remoteServiceName)) return;
    builder.port(uri.getPort());
    span.remoteEndpoint(builder.build());
  }

  static final class HttpAdapter extends
      brave.http.HttpAdapter<HttpRequest, ClientHttpResponse> {
    @Override public String method(HttpRequest request) {
      return request.getMethod().name();
    }

    @Override public String url(HttpRequest request) {
      return request.getURI().toString();
    }

    @Override public String requestHeader(HttpRequest request, String name) {
      Object result = request.getHeaders().getFirst(name);
      return result != null ? result.toString() : null;
    }

    @Override public Integer statusCode(ClientHttpResponse response) {
      try {
        return response.getRawStatusCode();
      } catch (IOException e) {
        return null;
      }
    }
  }
}

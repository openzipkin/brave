package brave.vertx.web.client;

import brave.Span;
import brave.Tracer;
import brave.http.HttpClientAdapter;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.propagation.Propagation.Setter;
import brave.propagation.TraceContext;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.net.SocketAddress;
import zipkin2.Endpoint;

// TODO: not actually an interceptor as we can't access types we need
// https://github.com/vert-x3/vertx-web/issues/891
final class TracingHttpClientRequestHandler implements Handler<HttpClientRequest> {
  static final Setter<HttpClientRequest, String> SETTER = new Setter<HttpClientRequest, String>() {
    @Override public void put(HttpClientRequest carrier, String key, String value) {
      carrier.putHeader(key, value);
    }

    @Override public String toString() {
      return "HttpClientRequest::putHeader";
    }
  };

  final Tracer tracer;
  final HttpClientHandler<HttpClientRequest, HttpClientResponse> handler;
  final TraceContext.Injector<HttpClientRequest> injector;
  final String serverName;

  TracingHttpClientRequestHandler(HttpTracing httpTracing) {
    tracer = httpTracing.tracing().tracer();
    handler = HttpClientHandler.create(httpTracing, new Adapter());
    injector = httpTracing.tracing().propagation().injector(SETTER);
    serverName = httpTracing.serverName();
  }

  @Override public void handle(HttpClientRequest request) {
    Span span = handler.handleSend(injector, request);
    request.exceptionHandler(event -> handler.handleReceive(null, event, span));
    request.handler(event -> {
      Endpoint.Builder endpoint = Endpoint.newBuilder();
      SocketAddress addr = event.request().connection().remoteAddress();
      if (endpoint.parseIp(addr.host()) || serverName != null) {
        span.remoteEndpoint(endpoint.serviceName(serverName).port(addr.port()).build());
      }
      handler.handleReceive(event, null, span);
    });
  }

  static final class Adapter extends HttpClientAdapter<HttpClientRequest, HttpClientResponse> {

    @Override public String method(HttpClientRequest request) {
      return request.method().name();
    }

    @Override public String path(HttpClientRequest request) {
      return request.path();
    }

    @Override public String url(HttpClientRequest request) {
      return request.absoluteURI();
    }

    @Override public String requestHeader(HttpClientRequest request, String name) {
      return request.headers().get(name);
    }

    @Override public Integer statusCode(HttpClientResponse response) {
      return statusCodeAsInt(response);
    }

    @Override public int statusCodeAsInt(HttpClientResponse response) {
      return response.statusCode();
    }
  }
}

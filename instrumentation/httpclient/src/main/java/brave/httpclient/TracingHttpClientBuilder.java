package brave.httpclient;

import brave.Span;
import brave.Tracer;
import brave.Tracer.SpanInScope;
import brave.Tracing;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.propagation.TraceContext;
import java.io.IOException;
import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.execchain.ClientExecChain;
import zipkin.Endpoint;

public final class TracingHttpClientBuilder extends HttpClientBuilder {

  public static HttpClientBuilder create(Tracing tracing) {
    return new TracingHttpClientBuilder(HttpTracing.create(tracing));
  }

  public static HttpClientBuilder create(HttpTracing httpTracing) {
    return new TracingHttpClientBuilder(httpTracing);
  }

  final Tracer tracer;
  final TraceContext.Injector<HttpRequestWrapper> injector;
  final HttpClientHandler<HttpRequestWrapper, HttpResponse> handler;

  TracingHttpClientBuilder(HttpTracing httpTracing) { // intentionally hidden
    if (httpTracing == null) throw new NullPointerException("HttpTracing == null");
    this.tracer = httpTracing.tracing().tracer();
    this.handler = HttpClientHandler.create(httpTracing, new HttpAdapter());
    this.injector = httpTracing.tracing().propagation().injector(HttpRequestWrapper::setHeader);
  }

  /**
   * protocol exec is the last in the execution chain, so first to execute. We eagerly create a span
   * here so that user interceptors can see it.
   */
  @Override protected ClientExecChain decorateProtocolExec(ClientExecChain protocolExec) {
    return (route, request, clientContext, execAware) -> {
      Span span = handler.nextSpan(request);
      CloseableHttpResponse response = null;
      Throwable error = null;
      try (SpanInScope ws = tracer.withSpanInScope(span)) {
        return response = protocolExec.execute(route, request, clientContext, execAware);
      } catch (IOException | HttpException | RuntimeException | Error e) {
        error = e;
        throw e;
      } finally {
        handler.handleReceive(response, error, span);
      }
    };
  }

  /**
   * Main exec is the first in the execution chain, so last to execute. This creates a concrete http
   * request, so this is where the span is started.
   */
  @Override protected ClientExecChain decorateMainExec(ClientExecChain exec) {
    return (route, request, context, execAware) -> {
      handler.handleSend(injector, request, tracer.currentSpan());
      return exec.execute(route, request, context, execAware);
    };
  }

  static final class HttpAdapter
      extends brave.http.HttpClientAdapter<HttpRequestWrapper, HttpResponse> {

    @Override
    public boolean parseServerAddress(HttpRequestWrapper httpRequest, Endpoint.Builder builder) {
      HttpHost target = httpRequest.getTarget();
      if (target == null) return false;
      if (builder.parseIp(target.getAddress()) || builder.parseIp(target.getHostName())) {
        builder.port(target.getPort());
        return true;
      }
      return false;
    }

    @Override public String method(HttpRequestWrapper request) {
      return request.getRequestLine().getMethod();
    }

    @Override public String path(HttpRequestWrapper request) {
      String result = request.getURI().getPath();
      int queryIndex = result.indexOf('?');
      return queryIndex == -1 ? result : result.substring(0, queryIndex);
    }

    @Override public String url(HttpRequestWrapper request) {
      HttpHost target = request.getTarget();
      if (target != null) return target.toURI() + request.getURI();
      return request.getRequestLine().getUri();
    }

    @Override public String requestHeader(HttpRequestWrapper request, String name) {
      Header result = request.getFirstHeader(name);
      return result != null ? result.getValue() : null;
    }

    @Override public Integer statusCode(HttpResponse response) {
      return response.getStatusLine().getStatusCode();
    }
  }
}

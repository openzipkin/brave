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
import org.apache.http.HttpMessage;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.protocol.HttpClientContext;
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
  final TraceContext.Injector<HttpMessage> injector;
  final HttpClientHandler<HttpRequestWrapper, HttpResponse> handler;
  final String remoteServiceName;

  TracingHttpClientBuilder(HttpTracing httpTracing) { // intentionally hidden
    if (httpTracing == null) throw new NullPointerException("HttpTracing == null");
    this.tracer = httpTracing.tracing().tracer();
    this.remoteServiceName = httpTracing.serverName();
    this.handler = new HttpClientHandler<>(new HttpAdapter(), httpTracing.clientParser());
    this.injector = httpTracing.tracing().propagation().injector(HttpMessage::setHeader);
  }

  /**
   * protocol exec is the second in the execution chain, so is invoked before a request is
   * provisioned. We provision and scope a span here, so that application interceptors can see
   * it via {@link Tracer#currentSpan()}.
   */
  @Override protected ClientExecChain decorateProtocolExec(ClientExecChain exec) {
    return (route, request, context, execAware) -> {
      Span next = tracer.nextSpan();
      context.setAttribute(SpanInScope.class.getName(), tracer.withSpanInScope(next));
      try {
        return exec.execute(route, request, context, execAware);
      } catch (IOException | HttpException | RuntimeException e) {
        context.getAttribute(SpanInScope.class.getName(), SpanInScope.class).close();
        throw e;
      }
    };
  }

  /**
   * main exec is the first in the execution chain, so last to execute. This creates a concrete http
   * request, so this is where the timing in the span occurs.
   *
   * <p>This ends the span (and scoping of it) created by {@link #decorateMainExec(ClientExecChain)}.
   */
  @Override protected ClientExecChain decorateMainExec(ClientExecChain exec) {
    return (route, request, context, execAware) -> {
      Span span = tracer.currentSpan();
      try {
        HttpHost host = HttpClientContext.adapt(context).getTargetHost();
        if (!span.isNoop() && host != null) {
          parseServerAddress(host, span);
          handler.handleSend(HttpRequestWrapper.wrap(request, host), span);
        } else {
          handler.handleSend(request, span);
        }

        injector.inject(span.context(), request);

        CloseableHttpResponse response = exec.execute(route, request, context, execAware);

        handler.handleReceive(response, span);
        return response;
      } catch (IOException | HttpException | RuntimeException e) {
        handler.handleError(e, span);
        throw e;
      } finally {
        context.getAttribute(SpanInScope.class.getName(), SpanInScope.class).close();
      }
    };
  }

  void parseServerAddress(HttpHost host, Span span) {
    Endpoint.Builder builder = Endpoint.builder().serviceName(remoteServiceName);
    if (!builder.parseIp(host.getAddress())) builder.parseIp(host.getHostName());
    builder.port(host.getPort());
    span.remoteEndpoint(builder.build());
  }

  static final class HttpAdapter extends brave.http.HttpAdapter<HttpRequestWrapper, HttpResponse> {
    @Override public String method(HttpRequestWrapper request) {
      return request.getRequestLine().getMethod();
    }

    @Override public String path(HttpRequestWrapper request) {
      String result = request.getRequestLine().getUri();
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

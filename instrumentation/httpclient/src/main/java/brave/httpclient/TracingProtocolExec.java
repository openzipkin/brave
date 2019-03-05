package brave.httpclient;

import brave.Span;
import brave.Tracer;
import brave.Tracer.SpanInScope;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import java.io.IOException;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpExecutionAware;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.execchain.ClientExecChain;

/**
 * protocol exec is the last in the execution chain, so first to execute. We eagerly create a span
 * here so that user interceptors can see it.
 */
final class TracingProtocolExec implements ClientExecChain {

  final Tracer tracer;
  final HttpClientHandler<HttpRequestWrapper, HttpResponse> handler;
  final ClientExecChain protocolExec;

  TracingProtocolExec(HttpTracing httpTracing, ClientExecChain protocolExec) {
    this.tracer = httpTracing.tracing().tracer();
    this.handler = HttpClientHandler.create(httpTracing, new HttpAdapter());
    this.protocolExec = protocolExec;
  }

  @Override public CloseableHttpResponse execute(HttpRoute route, HttpRequestWrapper request,
      HttpClientContext clientContext, HttpExecutionAware execAware)
      throws IOException, HttpException {
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
  }
}

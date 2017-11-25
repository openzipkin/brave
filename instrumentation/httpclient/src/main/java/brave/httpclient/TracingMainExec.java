package brave.httpclient;

import brave.Tracer;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.propagation.Propagation.Setter;
import brave.propagation.TraceContext;
import java.io.IOException;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpExecutionAware;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.execchain.ClientExecChain;
import org.apache.http.message.AbstractHttpMessage;

/**
 * Main exec is the first in the execution chain, so last to execute. This creates a concrete http
 * request, so this is where the span is started.
 */
final class TracingMainExec implements ClientExecChain {
  static final Setter<HttpRequestWrapper, String> SETTER = AbstractHttpMessage::setHeader;

  final Tracer tracer;
  final HttpClientHandler<HttpRequestWrapper, HttpResponse> handler;
  final TraceContext.Injector<HttpRequestWrapper> injector;
  final ClientExecChain mainExec;

  TracingMainExec(HttpTracing httpTracing, ClientExecChain mainExec) {
    this.tracer = httpTracing.tracing().tracer();
    this.handler = HttpClientHandler.create(httpTracing, new HttpAdapter());
    this.injector = httpTracing.tracing().propagation().injector(SETTER);
    this.mainExec = mainExec;
  }

  @Override public CloseableHttpResponse execute(HttpRoute route, HttpRequestWrapper request,
      HttpClientContext context, HttpExecutionAware execAware)
      throws IOException, HttpException {
    handler.handleSend(injector, request, tracer.currentSpan());
    return mainExec.execute(route, request, context, execAware);
  }
}
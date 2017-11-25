package brave.httpclient;

import brave.Tracing;
import brave.http.HttpTracing;
import org.apache.http.impl.client.cache.CachingHttpClientBuilder;
import org.apache.http.impl.execchain.ClientExecChain;

public final class TracingCachingHttpClientBuilder extends CachingHttpClientBuilder {

  public static CachingHttpClientBuilder create(Tracing tracing) {
    return new TracingCachingHttpClientBuilder(HttpTracing.create(tracing));
  }

  public static CachingHttpClientBuilder create(HttpTracing httpTracing) {
    return new TracingCachingHttpClientBuilder(httpTracing);
  }

  final HttpTracing httpTracing;

  TracingCachingHttpClientBuilder(HttpTracing httpTracing) { // intentionally hidden
    if (httpTracing == null) throw new NullPointerException("HttpTracing == null");
    this.httpTracing = httpTracing;
  }

  @Override protected ClientExecChain decorateProtocolExec(ClientExecChain protocolExec) {
    return new TracingProtocolExec(httpTracing, protocolExec);
  }

  @Override protected ClientExecChain decorateMainExec(ClientExecChain exec) {
    return new TracingMainExec(httpTracing, exec);
  }
}

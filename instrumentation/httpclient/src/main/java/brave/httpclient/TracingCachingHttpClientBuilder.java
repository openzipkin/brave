package brave.httpclient;

import brave.Span;
import brave.Tracing;
import brave.http.HttpTracing;
import org.apache.http.client.cache.CacheResponseStatus;
import org.apache.http.client.cache.HttpCacheContext;
import org.apache.http.impl.client.cache.CachingHttpClientBuilder;
import org.apache.http.impl.execchain.ClientExecChain;
import org.apache.http.protocol.HttpContext;

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
    return new LocalIfFromCacheTracingMainExec(httpTracing, super.decorateMainExec(exec));
  }

  static final class LocalIfFromCacheTracingMainExec extends TracingMainExec {
    LocalIfFromCacheTracingMainExec(HttpTracing httpTracing, ClientExecChain mainExec) {
      super(httpTracing, mainExec);
    }

    @Override boolean isRemote(HttpContext context, Span span) {
      boolean cacheHit = CacheResponseStatus.CACHE_HIT.equals(
          context.getAttribute(HttpCacheContext.CACHE_RESPONSE_STATUS));
      if (cacheHit) span.tag("http.cache_hit", "");
      return !cacheHit;
    }
  }
}

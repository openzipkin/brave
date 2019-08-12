/*
 * Copyright 2013-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
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

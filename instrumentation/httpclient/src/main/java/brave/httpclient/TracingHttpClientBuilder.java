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

import brave.Tracing;
import brave.http.HttpTracing;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.execchain.ClientExecChain;

public final class TracingHttpClientBuilder extends HttpClientBuilder {

  public static HttpClientBuilder create(Tracing tracing) {
    return new TracingHttpClientBuilder(HttpTracing.create(tracing));
  }

  public static HttpClientBuilder create(HttpTracing httpTracing) {
    return new TracingHttpClientBuilder(httpTracing);
  }

  final HttpTracing httpTracing;

  TracingHttpClientBuilder(HttpTracing httpTracing) { // intentionally hidden
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

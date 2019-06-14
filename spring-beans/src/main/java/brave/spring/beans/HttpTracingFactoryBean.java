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
package brave.spring.beans;

import brave.Tracing;
import brave.http.HttpClientParser;
import brave.http.HttpSampler;
import brave.http.HttpServerParser;
import brave.http.HttpTracing;
import org.springframework.beans.factory.FactoryBean;

/** Spring XML config does not support chained builders. This converts accordingly */
public class HttpTracingFactoryBean implements FactoryBean {

  Tracing tracing;
  HttpClientParser clientParser;
  HttpServerParser serverParser;
  HttpSampler clientSampler;
  HttpSampler serverSampler;

  @Override public HttpTracing getObject() {
    HttpTracing.Builder builder = HttpTracing.newBuilder(tracing);
    if (clientParser != null) builder.clientParser(clientParser);
    if (serverParser != null) builder.serverParser(serverParser);
    if (clientSampler != null) builder.clientSampler(clientSampler);
    if (serverSampler != null) builder.serverSampler(serverSampler);
    return builder.build();
  }

  @Override public Class<? extends HttpTracing> getObjectType() {
    return HttpTracing.class;
  }

  @Override public boolean isSingleton() {
    return true;
  }

  public void setTracing(Tracing tracing) {
    this.tracing = tracing;
  }

  public void setClientParser(HttpClientParser clientParser) {
    this.clientParser = clientParser;
  }

  public void setServerParser(HttpServerParser serverParser) {
    this.serverParser = serverParser;
  }

  public void setClientSampler(HttpSampler clientSampler) {
    this.clientSampler = clientSampler;
  }

  public void setServerSampler(HttpSampler serverSampler) {
    this.serverSampler = serverSampler;
  }
}

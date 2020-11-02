/*
 * Copyright 2013-2020 The OpenZipkin Authors
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
import brave.http.HttpRequest;
import brave.http.HttpRequestParser;
import brave.http.HttpResponseParser;
import brave.http.HttpServerParser;
import brave.http.HttpTracing;
import brave.http.HttpTracingCustomizer;
import brave.propagation.Propagation;
import brave.sampler.SamplerFunction;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.FactoryBean;

/** Spring XML config does not support chained builders. This converts accordingly */
public class HttpTracingFactoryBean implements FactoryBean {
  // Spring uses commons logging
  static final Log logger = LogFactory.getLog(HttpTracingFactoryBean.class);

  Tracing tracing;
  @Deprecated HttpClientParser clientParser;
  @Deprecated HttpServerParser serverParser;
  HttpRequestParser clientRequestParser, serverRequestParser;
  HttpResponseParser clientResponseParser, serverResponseParser;
  SamplerFunction<HttpRequest> clientSampler, serverSampler;
  Propagation<String> propagation;
  List<HttpTracingCustomizer> customizers;

  @Override public HttpTracing getObject() {
    HttpTracing.Builder builder = HttpTracing.newBuilder(tracing);
    if (clientParser != null) builder.clientParser(clientParser);
    if (clientRequestParser != null) builder.clientRequestParser(clientRequestParser);
    if (clientResponseParser != null) builder.clientResponseParser(clientResponseParser);
    if (serverRequestParser != null) builder.serverRequestParser(serverRequestParser);
    if (serverResponseParser != null) builder.serverResponseParser(serverResponseParser);
    if (serverParser != null) builder.serverParser(serverParser);
    if (clientSampler != null) builder.clientSampler(clientSampler);
    if (serverSampler != null) builder.serverSampler(serverSampler);
    if (propagation != null) builder.propagation(propagation);
    if (customizers != null) {
      for (HttpTracingCustomizer customizer : customizers) customizer.customize(builder);
    }
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

  @Deprecated public void setClientParser(HttpClientParser clientParser) {
    logger.warn("The property 'setClientParser' will be removed in a future release.\n"
      + "Use the property 'clientRequestParser' or 'clientResponseParser' instead");
    this.clientParser = clientParser;
  }

  public void setClientRequestParser(HttpRequestParser clientRequestParser) {
    this.clientRequestParser = clientRequestParser;
  }

  public void setClientResponseParser(HttpResponseParser clientResponseParser) {
    this.clientResponseParser = clientResponseParser;
  }

  public void setServerRequestParser(HttpRequestParser serverRequestParser) {
    this.serverRequestParser = serverRequestParser;
  }

  public void setServerResponseParser(HttpResponseParser serverResponseParser) {
    this.serverResponseParser = serverResponseParser;
  }

  @Deprecated public void setServerParser(HttpServerParser serverParser) {
    logger.warn("The property 'setServerParser' will be removed in a future release.\n"
      + "Use the property 'serverRequestParser' or 'serverResponseParser' instead");
    this.serverParser = serverParser;
  }

  public void setClientSampler(SamplerFunction<HttpRequest> clientSampler) {
    this.clientSampler = clientSampler;
  }

  public void setServerSampler(SamplerFunction<HttpRequest> serverSampler) {
    this.serverSampler = serverSampler;
  }

  public void setPropagation(Propagation<String> propagation) {
    this.propagation = propagation;
  }

  public void setCustomizers(List<HttpTracingCustomizer> customizers) {
    this.customizers = customizers;
  }
}

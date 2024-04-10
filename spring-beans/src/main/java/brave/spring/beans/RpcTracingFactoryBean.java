/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.spring.beans;

import brave.Tracing;
import brave.propagation.Propagation;
import brave.rpc.RpcRequest;
import brave.rpc.RpcRequestParser;
import brave.rpc.RpcResponseParser;
import brave.rpc.RpcTracing;
import brave.rpc.RpcTracingCustomizer;
import brave.sampler.SamplerFunction;
import java.util.List;
import org.springframework.beans.factory.FactoryBean;

/** Spring XML config does not support chained builders. This converts accordingly */
public class RpcTracingFactoryBean implements FactoryBean {
  Tracing tracing;
  SamplerFunction<RpcRequest> clientSampler, serverSampler;
  RpcRequestParser clientRequestParser, serverRequestParser;
  RpcResponseParser clientResponseParser, serverResponseParser;
  Propagation<String> propagation;
  List<RpcTracingCustomizer> customizers;

  @Override public RpcTracing getObject() {
    RpcTracing.Builder builder = RpcTracing.newBuilder(tracing);
    if (clientRequestParser != null) builder.clientRequestParser(clientRequestParser);
    if (clientResponseParser != null) builder.clientResponseParser(clientResponseParser);
    if (serverRequestParser != null) builder.serverRequestParser(serverRequestParser);
    if (serverResponseParser != null) builder.serverResponseParser(serverResponseParser);
    if (clientSampler != null) builder.clientSampler(clientSampler);
    if (serverSampler != null) builder.serverSampler(serverSampler);
    if (propagation != null) builder.propagation(propagation);
    if (customizers != null) {
      for (RpcTracingCustomizer customizer : customizers) customizer.customize(builder);
    }
    return builder.build();
  }

  @Override public Class<? extends RpcTracing> getObjectType() {
    return RpcTracing.class;
  }

  @Override public boolean isSingleton() {
    return true;
  }

  public void setTracing(Tracing tracing) {
    this.tracing = tracing;
  }

  public void setClientRequestParser(RpcRequestParser clientRequestParser) {
    this.clientRequestParser = clientRequestParser;
  }

  public void setClientResponseParser(RpcResponseParser clientResponseParser) {
    this.clientResponseParser = clientResponseParser;
  }

  public void setServerRequestParser(RpcRequestParser serverRequestParser) {
    this.serverRequestParser = serverRequestParser;
  }

  public void setServerResponseParser(RpcResponseParser serverResponseParser) {
    this.serverResponseParser = serverResponseParser;
  }

  public void setClientSampler(SamplerFunction<RpcRequest> clientSampler) {
    this.clientSampler = clientSampler;
  }

  public void setServerSampler(SamplerFunction<RpcRequest> serverSampler) {
    this.serverSampler = serverSampler;
  }

  public void setPropagation(Propagation<String> propagation) {
    this.propagation = propagation;
  }

  public void setCustomizers(List<RpcTracingCustomizer> customizers) {
    this.customizers = customizers;
  }
}

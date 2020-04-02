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
import brave.rpc.RpcRequest;
import brave.rpc.RpcTracing;
import brave.rpc.RpcTracingCustomizer;
import brave.sampler.SamplerFunction;
import java.util.List;
import org.springframework.beans.factory.FactoryBean;

/** Spring XML config does not support chained builders. This converts accordingly */
public class RpcTracingFactoryBean implements FactoryBean {
  Tracing tracing;
  SamplerFunction<RpcRequest> clientSampler, serverSampler;
  List<RpcTracingCustomizer> customizers;

  @Override public RpcTracing getObject() {
    RpcTracing.Builder builder = RpcTracing.newBuilder(tracing);
    if (clientSampler != null) builder.clientSampler(clientSampler);
    if (serverSampler != null) builder.serverSampler(serverSampler);
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

  public void setClientSampler(SamplerFunction<RpcRequest> clientSampler) {
    this.clientSampler = clientSampler;
  }

  public void setServerSampler(SamplerFunction<RpcRequest> serverSampler) {
    this.serverSampler = serverSampler;
  }

  public void setCustomizers(List<RpcTracingCustomizer> customizers) {
    this.customizers = customizers;
  }
}

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
package brave.rpc;

import brave.Tracing;
import brave.sampler.SamplerFunction;
import brave.sampler.SamplerFunctions;

/** @since 5.8 */
public class RpcTracing {
  /** @since 5.8 */
  public static RpcTracing create(Tracing tracing) {
    return newBuilder(tracing).build();
  }

  /** @since 5.8 */
  public static Builder newBuilder(Tracing tracing) {
    return new Builder(tracing);
  }

  /** @since 5.8 */
  public Tracing tracing() {
    return tracing;
  }

  /**
   * Returns an overriding sampling decision for a new trace. Defaults to ignore the request and use
   * the {@link SamplerFunctions#deferDecision() trace ID instead}.
   *
   * <p>This decision happens when a trace was not yet started in process. For example, you may be
   * making an rpc request as a part of booting your application. You may want to opt-out of tracing
   * client requests that did not originate from a server request.
   *
   * @see SamplerFunctions
   * @see RpcRuleSampler
   * @since 5.8
   */
  public SamplerFunction<RpcRequest> clientSampler() {
    return clientSampler;
  }

  /**
   * Returns an overriding sampling decision for a new trace. Defaults to ignore the request and use
   * the {@link SamplerFunctions#deferDecision() trace ID instead}.
   *
   * <p>This decision happens when trace IDs were not in headers, or a sampling decision has not
   * yet been made. For example, if a trace is already in progress, this function is not called. You
   * can implement this to skip paths that you never want to trace.
   *
   * @see SamplerFunctions
   * @see RpcRuleSampler
   * @since 5.8
   */
  public SamplerFunction<RpcRequest> serverSampler() {
    return serverSampler;
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  final Tracing tracing;
  final SamplerFunction<RpcRequest> clientSampler;
  final SamplerFunction<RpcRequest> serverSampler;

  RpcTracing(Builder builder) {
    this.tracing = builder.tracing;
    this.clientSampler = builder.clientSampler;
    this.serverSampler = builder.serverSampler;
  }

  public static final class Builder {
    Tracing tracing;
    SamplerFunction<RpcRequest> clientSampler;
    SamplerFunction<RpcRequest> serverSampler;

    Builder(Tracing tracing) {
      if (tracing == null) throw new NullPointerException("tracing == null");
      this.tracing = tracing;
      this.clientSampler = SamplerFunctions.deferDecision();
      this.serverSampler = SamplerFunctions.deferDecision();
    }

    Builder(RpcTracing source) {
      this.tracing = source.tracing;
      this.clientSampler = source.clientSampler;
      this.serverSampler = source.serverSampler;
    }

    /** @see RpcTracing#tracing() */
    public Builder tracing(Tracing tracing) {
      if (tracing == null) throw new NullPointerException("tracing == null");
      this.tracing = tracing;
      return this;
    }

    /** @see RpcTracing#clientSampler() */
    public Builder clientSampler(SamplerFunction<RpcRequest> clientSampler) {
      if (clientSampler == null) throw new NullPointerException("clientSampler == null");
      this.clientSampler = clientSampler;
      return this;
    }

    /** @see RpcTracing#serverSampler() */
    public Builder serverSampler(SamplerFunction<RpcRequest> serverSampler) {
      if (serverSampler == null) throw new NullPointerException("serverSampler == null");
      this.serverSampler = serverSampler;
      return this;
    }

    public RpcTracing build() {
      return new RpcTracing(this);
    }
  }
}

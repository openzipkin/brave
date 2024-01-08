/*
 * Copyright 2013-2024 The OpenZipkin Authors
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
package brave.grpc;

import brave.Tracing;
import brave.rpc.RpcTracing;
import io.grpc.ClientInterceptor;
import io.grpc.Metadata;
import io.grpc.ServerInterceptor;
import java.util.Map;

public final class GrpcTracing {
  public static GrpcTracing create(Tracing tracing) {
    return newBuilder(tracing).build();
  }

  public static GrpcTracing create(RpcTracing rpcTracing) {
    return newBuilder(rpcTracing).build();
  }

  public static Builder newBuilder(Tracing tracing) {
    return newBuilder(RpcTracing.create(tracing));
  }

  public static Builder newBuilder(RpcTracing rpcTracing) {
    return new Builder(rpcTracing);
  }

  public static final class Builder {
    RpcTracing rpcTracing;

    Builder(RpcTracing rpcTracing) {
      if (rpcTracing == null) throw new NullPointerException("rpcTracing == null");
      this.rpcTracing = rpcTracing;
    }

    Builder(GrpcTracing grpcTracing) {
      rpcTracing = grpcTracing.rpcTracing;
    }

    public GrpcTracing build() {
      return new GrpcTracing(this);
    }
  }

  final RpcTracing rpcTracing;
  final Map<String, Metadata.Key<String>> nameToKey;

  GrpcTracing(Builder builder) { // intentionally hidden constructor
    rpcTracing = builder.rpcTracing;
    nameToKey = GrpcPropagation.nameToKey(rpcTracing.propagation());
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  /** This interceptor traces outbound calls */
  public ClientInterceptor newClientInterceptor() {
    return new TracingClientInterceptor(this);
  }

  /** This interceptor traces inbound calls */
  public ServerInterceptor newServerInterceptor() {
    return new TracingServerInterceptor(this);
  }
}

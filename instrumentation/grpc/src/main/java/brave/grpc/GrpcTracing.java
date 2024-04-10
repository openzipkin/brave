/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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

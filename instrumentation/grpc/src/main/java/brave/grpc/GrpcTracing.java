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
package brave.grpc;

import brave.Tracing;
import brave.rpc.RpcRequestParser;
import brave.rpc.RpcResponseParser;
import brave.rpc.RpcTracing;
import io.grpc.ClientInterceptor;
import io.grpc.Metadata;
import io.grpc.ServerInterceptor;
import java.util.Map;

import static brave.grpc.GrpcParser.LEGACY_RESPONSE_PARSER;

public final class GrpcTracing {
  public static GrpcTracing create(Tracing tracing) {
    return newBuilder(tracing).build();
  }

  public static GrpcTracing create(RpcTracing rpcTracing) {
    return newBuilder(rpcTracing).build();
  }

  public static Builder newBuilder(Tracing tracing) {
    return newBuilder(RpcTracing.newBuilder(tracing)
      .clientResponseParser(LEGACY_RESPONSE_PARSER)
      .serverResponseParser(LEGACY_RESPONSE_PARSER)
      .build());
  }

  public static Builder newBuilder(RpcTracing rpcTracing) {
    return new Builder(rpcTracing);
  }

  public static final class Builder {
    RpcTracing rpcTracing;
    boolean grpcPropagationFormatEnabled = false;

    // for interop with old parsers
    MessageProcessor clientMessageProcessor = MessageProcessor.NOOP;
    MessageProcessor serverMessageProcessor = MessageProcessor.NOOP;

    Builder(RpcTracing rpcTracing) {
      if (rpcTracing == null) throw new NullPointerException("rpcTracing == null");
      this.rpcTracing = rpcTracing;
    }

    Builder(GrpcTracing grpcTracing) {
      rpcTracing = grpcTracing.rpcTracing;
      clientMessageProcessor = grpcTracing.clientMessageProcessor;
      serverMessageProcessor = grpcTracing.serverMessageProcessor;
    }

    /**
     * @see GrpcClientRequest
     * @see GrpcClientResponse
     * @deprecated Since 5.12 use {@link RpcTracing.Builder#clientRequestParser(RpcRequestParser)}
     * or {@link RpcTracing.Builder#clientResponseParser(RpcResponseParser)}.
     */
    @Deprecated
    public Builder clientParser(GrpcClientParser clientParser) {
      if (clientParser == null) throw new NullPointerException("clientParser == null");
      clientMessageProcessor = clientParser;
      rpcTracing = rpcTracing.toBuilder()
        .clientRequestParser(clientParser)
        .clientResponseParser(clientParser).build();
      return this;
    }

    /**
     * @see GrpcServerRequest
     * @see GrpcServerResponse
     * @deprecated Since 5.12 use {@link RpcTracing.Builder#serverRequestParser(RpcRequestParser)}
     * or {@link RpcTracing.Builder#serverResponseParser(RpcResponseParser)}.
     */
    @Deprecated
    public Builder serverParser(GrpcServerParser serverParser) {
      if (serverParser == null) throw new NullPointerException("serverParser == null");
      serverMessageProcessor = serverParser;
      rpcTracing = rpcTracing.toBuilder()
        .serverRequestParser(serverParser)
        .serverResponseParser(serverParser).build();
      return this;
    }

    /**
     * When true, "grpc-trace-bin" is preferred when extracting trace context. This is useful when
     * <a href="https://opencensus.io/">OpenCensus</a> implements tracing upstream or downstream.
     * Default is false.
     *
     * <p>This wraps an existing propagation implementation, but prefers extracting
     * "grpc-trace-bin" when parsing gRPC metadata. Regardless of whether "grpc-trace-bin" was
     * parsed, it is speculatively written on outgoing requests.
     *
     * <p>When present, "grpc-tags-bin" is propagated pass-through. We do not alter it.
     *
     * <p>Warning: the format of both "grpc-trace-bin" is version 0. As such,
     * consider this feature experimental.
     *
     * @deprecated The only user of this format was Census, which was removed from gRPC in version
     * v1.27.
     */
    @Deprecated public Builder grpcPropagationFormatEnabled(boolean grpcPropagationFormatEnabled) {
      this.grpcPropagationFormatEnabled = grpcPropagationFormatEnabled;
      return this;
    }

    public GrpcTracing build() {
      return new GrpcTracing(this);
    }
  }

  final RpcTracing rpcTracing;
  final Map<String, Metadata.Key<String>> nameToKey;
  final boolean grpcPropagationFormatEnabled;

  // for toBuilder()
  final MessageProcessor clientMessageProcessor, serverMessageProcessor;

  GrpcTracing(Builder builder) { // intentionally hidden constructor
    grpcPropagationFormatEnabled = builder.grpcPropagationFormatEnabled;

    // Decorate so that grpc-specific formats are sent downstream
    if (grpcPropagationFormatEnabled) {
      rpcTracing = builder.rpcTracing.toBuilder()
        .propagation(GrpcPropagation.create(builder.rpcTracing.propagation()))
        .build();
    } else {
      rpcTracing = builder.rpcTracing;
    }

    nameToKey = GrpcPropagation.nameToKey(rpcTracing.propagation());
    clientMessageProcessor = builder.clientMessageProcessor;
    serverMessageProcessor = builder.serverMessageProcessor;
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  /** This interceptor traces outbound calls */
  public final ClientInterceptor newClientInterceptor() {
    return new TracingClientInterceptor(this);
  }

  /** This interceptor traces inbound calls */
  public ServerInterceptor newServerInterceptor() {
    return new TracingServerInterceptor(this);
  }
}

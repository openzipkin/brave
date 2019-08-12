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
package brave.grpc;

import brave.ErrorParser;
import brave.Tracing;
import brave.propagation.Propagation;
import io.grpc.ClientInterceptor;
import io.grpc.Metadata;
import io.grpc.ServerInterceptor;

public final class GrpcTracing {
  public static GrpcTracing create(Tracing tracing) {
    if (tracing == null) throw new NullPointerException("tracing == null");
    return new Builder(tracing).build();
  }

  public static Builder newBuilder(Tracing tracing) {
    return new Builder(tracing);
  }

  public static final class Builder {
    final Tracing tracing;
    GrpcClientParser clientParser;
    GrpcServerParser serverParser;
    boolean grpcPropagationFormatEnabled = false;

    Builder(Tracing tracing) {
      this.tracing = tracing;
      // override to re-use any custom error parser from the tracing component
      ErrorParser errorParser = tracing.errorParser();
      clientParser = new GrpcClientParser() {
        @Override protected ErrorParser errorParser() {
          return errorParser;
        }
      };
      serverParser = new GrpcServerParser() {
        @Override protected ErrorParser errorParser() {
          return errorParser;
        }
      };
    }

    public Builder clientParser(GrpcClientParser clientParser) {
      if (clientParser == null) throw new NullPointerException("clientParser == null");
      this.clientParser = clientParser;
      return this;
    }

    public Builder serverParser(GrpcServerParser serverParser) {
      if (serverParser == null) throw new NullPointerException("serverParser == null");
      this.serverParser = serverParser;
      return this;
    }

    /**
     * When true, "grpc-trace-bin" is preferred when extracting trace context. This is useful when
     * <a href="https://opencensus.io/">OpenCensus</a> implements tracing upstream or downstream.
     * Default is false.
     *
     * <p>This wraps an existing propagation implementation, but prefers extracting
     * "grpc-trace-bin"
     * and "grpc-tags-bin" when parsing gRPC metadata. The incoming service method is propagated to
     * outgoing client requests and written in the tags context as the key named "method".
     * Regardless of whether "grpc-trace-bin" was parsed, it is speculatively written on outgoing
     * requests.
     *
     * <p>Warning: the format of both "grpc-trace-bin" and "grpc-tags-bin" are version 0. As such,
     * consider this feature experimental.
     */
    public Builder grpcPropagationFormatEnabled(boolean grpcPropagationFormatEnabled) {
      this.grpcPropagationFormatEnabled = grpcPropagationFormatEnabled;
      return this;
    }

    public GrpcTracing build() {
      return new GrpcTracing(this);
    }
  }

  final Tracing tracing;
  final Propagation<Metadata.Key<String>> propagation;
  final GrpcClientParser clientParser;
  final GrpcServerParser serverParser;
  final boolean grpcPropagationFormatEnabled;

  GrpcTracing(Builder builder) { // intentionally hidden constructor
    tracing = builder.tracing;
    grpcPropagationFormatEnabled = builder.grpcPropagationFormatEnabled;
    Propagation.Factory propagationFactory = tracing.propagationFactory();
    if (grpcPropagationFormatEnabled) {
      propagationFactory = GrpcPropagation.newFactory(propagationFactory);
    }
    propagation = propagationFactory.create(AsciiMetadataKeyFactory.INSTANCE);
    clientParser = builder.clientParser;
    serverParser = builder.serverParser;
  }

  public Builder toBuilder() {
    return new Builder(tracing)
      .clientParser(clientParser)
      .serverParser(serverParser);
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

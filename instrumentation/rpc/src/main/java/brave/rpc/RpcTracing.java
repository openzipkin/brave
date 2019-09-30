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
package brave.rpc;

import brave.Span;
import brave.Tracing;
import brave.internal.Nullable;
import brave.sampler.SamplerFunction;
import brave.sampler.SamplerFunctions;
import java.io.Closeable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Instances built via {@link #create(Tracing)} or {@link #newBuilder(Tracing)} are registered
 * automatically such that statically configured instrumentation like RPC clients can use {@link
 * #current()}.
 *
 * @since 5.8
 */
public class RpcTracing implements Closeable {
  static final AtomicReference<RpcTracing> CURRENT = new AtomicReference<>();

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
   * Used by {@link RpcClientHandler#handleSend(RpcClientRequest)} to add a span name and tags about
   * the request before it is sent to the server.
   *
   * @since 5.12
   */
  public RpcRequestParser clientRequestParser() {
    return clientRequestParser;
  }

  /**
   * Used by {@link RpcClientHandler#handleReceive(RpcClientResponse, Span)} to add tags about the
   * response received from the server.
   *
   * @since 5.12
   */
  public RpcResponseParser clientResponseParser() {
    return clientResponseParser;
  }

  /**
   * Used by {@link RpcServerHandler#handleReceive(RpcServerRequest)} to add a span name and tags
   * about the request before the server processes it.
   *
   * @since 5.12
   */
  public RpcRequestParser serverRequestParser() {
    return serverRequestParser;
  }

  /**
   * Used by {@link RpcServerHandler#handleSend(RpcServerResponse, Span)}  to add tags about the
   * response sent to the client.
   *
   * @since 5.12
   */
  public RpcResponseParser serverResponseParser() {
    return serverResponseParser;
  }

  /**
   * Returns an overriding sampling decision for a new trace. Defaults to ignore the request and use
   * the {@link SamplerFunctions#deferDecision() trace ID instead}.
   *
   * <p>This decision happens when a trace was not yet started in process. For example, you may be
   * making an RPC request as a part of booting your application. You may want to opt-out of tracing
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
  final RpcRequestParser clientRequestParser, serverRequestParser;
  final RpcResponseParser clientResponseParser, serverResponseParser;
  final SamplerFunction<RpcRequest> clientSampler, serverSampler;

  RpcTracing(Builder builder) {
    this.tracing = builder.tracing;
    this.clientRequestParser = builder.clientRequestParser;
    this.serverRequestParser = builder.serverRequestParser;
    this.clientResponseParser = builder.clientResponseParser;
    this.serverResponseParser = builder.serverResponseParser;
    this.clientSampler = builder.clientSampler;
    this.serverSampler = builder.serverSampler;
    // assign current IFF there's no instance already current
    CURRENT.compareAndSet(null, this);
  }

  public static final class Builder {
    Tracing tracing;
    RpcRequestParser clientRequestParser, serverRequestParser;
    RpcResponseParser clientResponseParser, serverResponseParser;
    SamplerFunction<RpcRequest> clientSampler, serverSampler;

    Builder(Tracing tracing) {
      if (tracing == null) throw new NullPointerException("tracing == null");
      this.tracing = tracing;
      this.clientRequestParser = this.serverRequestParser = RpcRequestParser.DEFAULT;
      this.clientResponseParser = this.serverResponseParser = RpcResponseParser.DEFAULT;
      this.clientSampler = SamplerFunctions.deferDecision();
      this.serverSampler = SamplerFunctions.deferDecision();
    }

    Builder(RpcTracing source) {
      this.tracing = source.tracing;
      this.clientRequestParser = source.clientRequestParser;
      this.serverRequestParser = source.serverRequestParser;
      this.clientResponseParser = source.clientResponseParser;
      this.serverResponseParser = source.serverResponseParser;
      this.clientSampler = source.clientSampler;
      this.serverSampler = source.serverSampler;
    }

    /** @see RpcTracing#tracing() */
    public Builder tracing(Tracing tracing) {
      if (tracing == null) throw new NullPointerException("tracing == null");
      this.tracing = tracing;
      return this;
    }

    /**
     * Overrides the tagging policy for RPC client requests.
     *
     * @see RpcTracing#clientRequestParser()
     * @since 5.12
     */
    public Builder clientRequestParser(RpcRequestParser clientRequestParser) {
      if (clientRequestParser == null) {
        throw new NullPointerException("clientRequestParser == null");
      }
      this.clientRequestParser = clientRequestParser;
      return this;
    }

    /**
     * Overrides the tagging policy for RPC client responses.
     *
     * @see RpcTracing#clientResponseParser()
     * @since 5.12
     */
    public Builder clientResponseParser(RpcResponseParser clientResponseParser) {
      if (clientResponseParser == null) {
        throw new NullPointerException("clientResponseParser == null");
      }
      this.clientResponseParser = clientResponseParser;
      return this;
    }

    /**
     * Overrides the tagging policy for RPC server requests.
     *
     * @see RpcTracing#serverRequestParser()
     * @since 5.12
     */
    public Builder serverRequestParser(RpcRequestParser serverRequestParser) {
      if (serverRequestParser == null) {
        throw new NullPointerException("serverRequestParser == null");
      }
      this.serverRequestParser = serverRequestParser;
      return this;
    }

    /**
     * Overrides the tagging policy for RPC server responses.
     *
     * @see RpcTracing#serverResponseParser()
     * @since 5.12
     */
    public Builder serverResponseParser(RpcResponseParser serverResponseParser) {
      if (serverResponseParser == null) {
        throw new NullPointerException("serverResponseParser == null");
      }
      this.serverResponseParser = serverResponseParser;
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

  /**
   * Returns the most recently created tracing component iff it hasn't been closed. null otherwise.
   *
   * <p>This object should not be cached.
   *
   * @since 5.9
   */
  @Nullable public static RpcTracing current() {
    return CURRENT.get();
  }

  /** @since 5.9 */
  @Override public void close() {
    // only set null if we are the outer-most instance
    CURRENT.compareAndSet(this, null);
  }
}

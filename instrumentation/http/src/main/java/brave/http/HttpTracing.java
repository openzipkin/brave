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
package brave.http;

import brave.ErrorParser;
import brave.Tracing;
import brave.sampler.SamplerFunction;
import brave.sampler.SamplerFunctions;

import static brave.http.HttpSampler.fromHttpRequestSampler;
import static brave.http.HttpSampler.toHttpRequestSampler;

public class HttpTracing { // Not final as it previously was not. This allows mocks and similar.
  public static HttpTracing create(Tracing tracing) {
    return newBuilder(tracing).build();
  }

  public static Builder newBuilder(Tracing tracing) {
    return new Builder(tracing);
  }

  public Tracing tracing() {
    return tracing;
  }

  public HttpClientParser clientParser() {
    return clientParser;
  }

  /**
   * Used by http clients to indicate the name of the destination service.
   *
   * Defaults to "", which will not show in the zipkin UI or end up in the dependency graph.
   *
   * <p>When present, a link from {@link Tracing.Builder#localServiceName(String)} to this name
   * will increment for each traced client call.
   *
   * <p>As this is endpoint-specific, it is typical to create a scoped instance of {@linkplain
   * HttpTracing} to assign this value.
   *
   * For example:
   * <pre>{@code
   * github = TracingHttpClientBuilder.create(httpTracing.serverName("github"));
   * }</pre>
   *
   * @see HttpClientHandler
   * @see brave.Span#remoteServiceName(String)
   */
  public String serverName() {
    return serverName;
  }

  /**
   * Scopes this component for a client of the indicated server.
   *
   * @see #serverName()
   */
  public HttpTracing clientOf(String serverName) {
    return toBuilder().serverName(serverName).build();
  }

  public HttpServerParser serverParser() {
    return serverParser;
  }

  /** @deprecated Since 5.8, use {@link #clientRequestSampler()} */
  @Deprecated public HttpSampler clientSampler() {
    return fromHttpRequestSampler(clientSampler);
  }

  /**
   * Returns an overriding sampling decision for a new trace. Defaults to ignore the request and use
   * the {@link SamplerFunctions#deferDecision() trace ID instead}.
   *
   * <p>This decision happens when a trace was not yet started in process. For example, you may be
   * making an http request as a part of booting your application. You may want to opt-out of
   * tracing client requests that did not originate from a server request.
   *
   * @see SamplerFunctions
   * @since 5.8
   */
  public SamplerFunction<HttpRequest> clientRequestSampler() {
    return clientSampler;
  }

  /** @deprecated Since 5.8, use {@link #serverRequestSampler()} */
  @Deprecated public HttpSampler serverSampler() {
    return fromHttpRequestSampler(serverSampler);
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
   * @since 5.8
   */
  public SamplerFunction<HttpRequest> serverRequestSampler() {
    return serverSampler;
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  final Tracing tracing;
  final HttpClientParser clientParser;
  final String serverName;
  final HttpServerParser serverParser;
  final SamplerFunction<HttpRequest> clientSampler, serverSampler;

  HttpTracing(Builder builder) {
    this.tracing = builder.tracing;
    this.clientParser = builder.clientParser;
    this.serverName = builder.serverName;
    this.serverParser = builder.serverParser;
    this.clientSampler = builder.clientSampler;
    this.serverSampler = builder.serverSampler;
  }

  public static final class Builder {
    Tracing tracing;
    HttpClientParser clientParser;
    String serverName;
    HttpServerParser serverParser;
    SamplerFunction<HttpRequest> clientSampler, serverSampler;

    Builder(Tracing tracing) {
      if (tracing == null) throw new NullPointerException("tracing == null");
      final ErrorParser errorParser = tracing.errorParser();
      this.tracing = tracing;
      this.serverName = "";
      // override to re-use any custom error parser from the tracing component
      this.clientParser = new HttpClientParser() {
        @Override protected ErrorParser errorParser() {
          return errorParser;
        }
      };
      this.serverParser = new HttpServerParser() {
        @Override protected ErrorParser errorParser() {
          return errorParser;
        }
      };
      this.clientSampler = SamplerFunctions.deferDecision();
      this.serverSampler = SamplerFunctions.deferDecision();
    }

    Builder(HttpTracing source) {
      this.tracing = source.tracing;
      this.clientParser = source.clientParser;
      this.serverName = source.serverName;
      this.serverParser = source.serverParser;
      this.clientSampler = source.clientSampler;
      this.serverSampler = source.serverSampler;
    }

    /** @see HttpTracing#tracing() */
    public Builder tracing(Tracing tracing) {
      if (tracing == null) throw new NullPointerException("tracing == null");
      this.tracing = tracing;
      return this;
    }

    /**
     * Overrides the tagging policy for http client spans.
     *
     * @see HttpParser#errorParser() for advice when making custom types
     * @see HttpTracing#clientParser()
     */
    public Builder clientParser(HttpClientParser clientParser) {
      if (clientParser == null) throw new NullPointerException("clientParser == null");
      this.clientParser = clientParser;
      return this;
    }

    Builder serverName(String serverName) {
      if (serverName == null) throw new NullPointerException("serverName == null");
      this.serverName = serverName;
      return this;
    }

    /**
     * Overrides the tagging policy for http client spans.
     *
     * @see HttpParser#errorParser() for advice when making custom types
     * @see HttpTracing#serverParser()
     */
    public Builder serverParser(HttpServerParser serverParser) {
      if (serverParser == null) throw new NullPointerException("serverParser == null");
      this.serverParser = serverParser;
      return this;
    }

    /** @deprecated Since 5.8, use {@link #clientSampler(SamplerFunction)} */
    public Builder clientSampler(HttpSampler clientSampler) {
      if (clientSampler == null) throw new NullPointerException("clientSampler == null");
      return clientSampler((SamplerFunction<HttpRequest>) clientSampler);
    }

    /**
     * @see SamplerFunctions
     * @see HttpTracing#clientRequestSampler()
     * @since 5.8
     */
    public Builder clientSampler(SamplerFunction<HttpRequest> clientSampler) {
      if (clientSampler == null) throw new NullPointerException("clientSampler == null");
      this.clientSampler = toHttpRequestSampler(clientSampler);
      return this;
    }

    /** @deprecated Since 5.8, use {@link #serverSampler(SamplerFunction)} */
    public Builder serverSampler(HttpSampler serverSampler) {
      return serverSampler((SamplerFunction<HttpRequest>) serverSampler);
    }

    /**
     * @see SamplerFunctions
     * @see HttpTracing#serverRequestSampler()
     * @since 5.8
     */
    public Builder serverSampler(SamplerFunction<HttpRequest> serverSampler) {
      if (serverSampler == null) throw new NullPointerException("serverSampler == null");
      this.serverSampler = toHttpRequestSampler(serverSampler);
      return this;
    }

    public HttpTracing build() {
      return new HttpTracing(this);
    }
  }
}

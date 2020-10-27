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
package brave.http;

import brave.Span;
import brave.Tracing;
import brave.baggage.BaggagePropagation;
import brave.internal.Nullable;
import brave.propagation.B3Propagation;
import brave.propagation.Propagation;
import brave.sampler.SamplerFunction;
import brave.sampler.SamplerFunctions;
import java.io.Closeable;
import java.util.concurrent.atomic.AtomicReference;

import static brave.http.HttpSampler.fromHttpRequestSampler;
import static brave.http.HttpSampler.toHttpRequestSampler;

/**
 * Instances built via {@link #create(Tracing)} or {@link #newBuilder(Tracing)} are registered
 * automatically such that statically configured instrumentation like HTTP clients can use {@link
 * #current()}.
 */
// Not final as it previously was not. This allows mocks and similar.
public class HttpTracing implements Closeable {
  static final AtomicReference<HttpTracing> CURRENT = new AtomicReference<>();

  public static HttpTracing create(Tracing tracing) {
    return newBuilder(tracing).build();
  }

  public static Builder newBuilder(Tracing tracing) {
    return new Builder(tracing);
  }

  public Tracing tracing() {
    return tracing;
  }

  /**
   * Used by {@link HttpClientHandler#handleSend(HttpClientRequest)} to add a span name and tags
   * about the request before it is sent to the server.
   *
   * @since 5.10
   */
  public HttpRequestParser clientRequestParser() {
    return clientRequestParser;
  }

  /**
   * Used by {@link HttpClientHandler#handleReceive(HttpClientResponse, Span)} to add tags about the
   * response received from the server.
   *
   * @since 5.10
   */
  public HttpResponseParser clientResponseParser() {
    return clientResponseParser;
  }

  /**
   * @deprecated Since 5.10, use {@link #clientRequestParser()} and {@link #clientResponseParser()}
   */
  @Deprecated public HttpClientParser clientParser() {
    if (clientRequestParser instanceof HttpRequestParserAdapters.ClientAdapter) {
      return (HttpClientParser) ((HttpRequestParserAdapters.ClientAdapter) clientRequestParser).parser;
    }
    return new HttpClientParserAdapter(
      clientRequestParser,
      clientResponseParser,
      tracing.currentTraceContext(),
      tracing.errorParser()
    );
  }

  /**
   * Used by http clients to indicate the name of the destination service.
   * <p>
   * Defaults to "", which will not show in the zipkin UI or end up in the dependency graph.
   *
   * <p>When present, a link from {@link Tracing.Builder#localServiceName(String)} to this name
   * will increment for each traced client call.
   *
   * <p>As this is endpoint-specific, it is typical to create a scoped instance of {@linkplain
   * HttpTracing} to assign this value.
   * <p>
   * For example:
   * <pre>{@code
   * github = TracingHttpClientBuilder.create(httpTracing.clientOf("github"));
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

  /**
   * Used by {@link HttpServerHandler#handleReceive(HttpServerRequest)} to add a span name and tags
   * about the request before the server processes it.
   *
   * @since 5.10
   */
  public HttpRequestParser serverRequestParser() {
    return serverRequestParser;
  }

  /**
   * Used by {@link HttpServerHandler#handleSend(Object, Throwable, Span)} to add tags about the
   * response sent to the client.
   *
   * @since 5.10
   */
  public HttpResponseParser serverResponseParser() {
    return serverResponseParser;
  }

  /**
   * @deprecated Since 5.10, use {@link #serverRequestParser()} and {@link #serverResponseParser()}
   */
  @Deprecated public HttpServerParser serverParser() {
    if (serverRequestParser instanceof HttpRequestParserAdapters.ServerAdapter) {
      return (HttpServerParser) ((HttpRequestParserAdapters.ServerAdapter) serverRequestParser).parser;
    }
    return new HttpServerParserAdapter(
      serverRequestParser,
      serverResponseParser,
      tracing.currentTraceContext(),
      tracing.errorParser()
    );
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

  /**
   * Returns the propagation component used by HTTP instrumentation.
   *
   * <p>Typically, this is the same as {@link Tracing#propagation()}. Overrides will apply to all
   * HTTP instrumentation in use. For example, Servlet and also OkHttp. If only trying to change B3
   * related headers, use the more efficient {@link B3Propagation.FactoryBuilder#injectFormat(Span.Kind,
   * B3Propagation.Format)} instead.
   *
   * <h3>Use caution when overriding</h3>
   * If overriding this via {@link Builder#propagation(Propagation)}, take care to also delegate to
   * {@link Tracing#propagation()}. Otherwise, you can break features something else may have set,
   * such as {@link BaggagePropagation}.
   *
   * <h3>Library-specific formats</h3>
   * HTTP instrumentation can localize propagation changes by calling {@link #toBuilder()}, then
   * {@link Builder#propagation(Propagation)}. This allows library-specific formats.
   *
   * <p>For example, cloud SDKs often use HTTP (or REST) APIs for communication. These endpoints
   * may hemselves be traceable, in a cloud-specific or otherwise different format than what general
   * applications use. SDK instrumentation can override the propagation component to solely use
   * their format, and drop any {@link BaggagePropagation baggage}.
   *
   * @see Tracing#propagation()
   * @since 5.13
   */
  public Propagation<String> propagation() {
    return propagation;
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  final Tracing tracing;
  final HttpRequestParser clientRequestParser, serverRequestParser;
  final HttpResponseParser clientResponseParser, serverResponseParser;
  final SamplerFunction<HttpRequest> clientSampler, serverSampler;
  final Propagation<String> propagation;
  final String serverName;

  HttpTracing(Builder builder) {
    this.tracing = builder.tracing;
    this.clientRequestParser = builder.clientRequestParser;
    this.serverRequestParser = builder.serverRequestParser;
    this.clientResponseParser = builder.clientResponseParser;
    this.serverResponseParser = builder.serverResponseParser;
    this.clientSampler = builder.clientSampler;
    this.serverSampler = builder.serverSampler;
    this.propagation = builder.propagation;
    this.serverName = builder.serverName;
    // assign current IFF there's no instance already current
    CURRENT.compareAndSet(null, this);
  }

  public static final class Builder {
    Tracing tracing;
    HttpRequestParser clientRequestParser, serverRequestParser;
    HttpResponseParser clientResponseParser, serverResponseParser;
    SamplerFunction<HttpRequest> clientSampler, serverSampler;
    Propagation<String> propagation;
    String serverName;

    Builder(Tracing tracing) {
      if (tracing == null) throw new NullPointerException("tracing == null");
      this.tracing = tracing;
      this.clientRequestParser = this.serverRequestParser = HttpRequestParser.DEFAULT;
      this.clientResponseParser = this.serverResponseParser = HttpResponseParser.DEFAULT;
      this.clientSampler = this.serverSampler = SamplerFunctions.deferDecision();
      this.propagation = tracing.propagation();
      this.serverName = "";
    }

    Builder(HttpTracing source) {
      this.tracing = source.tracing;
      this.clientRequestParser = source.clientRequestParser;
      this.serverRequestParser = source.serverRequestParser;
      this.clientResponseParser = source.clientResponseParser;
      this.serverResponseParser = source.serverResponseParser;
      this.clientSampler = source.clientSampler;
      this.serverSampler = source.serverSampler;
      this.propagation = source.propagation;
      this.serverName = source.serverName;
    }

    /** @see HttpTracing#tracing() */
    public Builder tracing(Tracing tracing) {
      if (tracing == null) throw new NullPointerException("tracing == null");
      this.tracing = tracing;
      return this;
    }

    /**
     * Overrides the tagging policy for HTTP client requests.
     *
     * @see HttpTracing#clientRequestParser()
     * @since 5.10
     */
    public Builder clientRequestParser(HttpRequestParser clientRequestParser) {
      if (clientRequestParser == null) {
        throw new NullPointerException("clientRequestParser == null");
      }
      this.clientRequestParser = clientRequestParser;
      return this;
    }

    /**
     * Overrides the tagging policy for HTTP client responses.
     *
     * @see HttpTracing#clientResponseParser()
     * @since 5.10
     */
    public Builder clientResponseParser(HttpResponseParser clientResponseParser) {
      if (clientResponseParser == null) {
        throw new NullPointerException("clientResponseParser == null");
      }
      this.clientResponseParser = clientResponseParser;
      return this;
    }

    /**
     * @deprecated Since 5.10, use {@link #clientRequestParser(HttpRequestParser)} and {@link
     * #clientResponseParser(HttpResponseParser)}
     */
    @Deprecated public Builder clientParser(HttpClientParser clientParser) {
      if (clientParser == null) throw new NullPointerException("clientParser == null");
      this.clientRequestParser =
        new HttpRequestParserAdapters.ClientAdapter(tracing.currentTraceContext(), clientParser);
      this.clientResponseParser =
        new HttpResponseParserAdapters.ClientAdapter(tracing.currentTraceContext(), clientParser);
      this.tracing.errorParser();
      return this;
    }

    Builder serverName(String serverName) {
      if (serverName == null) throw new NullPointerException("serverName == null");
      this.serverName = serverName;
      return this;
    }

    /**
     * Overrides the tagging policy for HTTP server requests.
     *
     * @see HttpTracing#serverRequestParser()
     * @since 5.10
     */
    public Builder serverRequestParser(HttpRequestParser serverRequestParser) {
      if (serverRequestParser == null) {
        throw new NullPointerException("serverRequestParser == null");
      }
      this.serverRequestParser = serverRequestParser;
      return this;
    }

    /**
     * Overrides the tagging policy for HTTP server responses.
     *
     * @see HttpTracing#serverResponseParser()
     * @since 5.10
     */
    public Builder serverResponseParser(HttpResponseParser serverResponseParser) {
      if (serverResponseParser == null) {
        throw new NullPointerException("serverResponseParser == null");
      }
      this.serverResponseParser = serverResponseParser;
      return this;
    }

    /**
     * @deprecated Since 5.10, use {@link #serverRequestParser(HttpRequestParser)} and {@link
     * #serverResponseParser(HttpResponseParser)}
     */
    @Deprecated public Builder serverParser(HttpServerParser serverParser) {
      if (serverParser == null) throw new NullPointerException("serverParser == null");
      this.serverRequestParser =
        new HttpRequestParserAdapters.ServerAdapter(tracing.currentTraceContext(), serverParser);
      this.serverResponseParser =
        new HttpResponseParserAdapters.ServerAdapter(tracing.currentTraceContext(), serverParser);
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

    /** @see HttpTracing#propagation() */
    public Builder propagation(Propagation<String> propagation) {
      if (propagation == null) throw new NullPointerException("propagation == null");
      this.propagation = propagation;
      return this;
    }

    public HttpTracing build() {
      return new HttpTracing(this);
    }
  }

  /**
   * Returns the most recently created tracing component iff it hasn't been closed. null otherwise.
   *
   * <p>This object should not be cached.
   *
   * @since 5.9
   */
  @Nullable public static HttpTracing current() {
    return CURRENT.get();
  }

  /** @since 5.9 */
  @Override public void close() {
    // only set null if we are the outer-most instance
    CURRENT.compareAndSet(this, null);
  }
}

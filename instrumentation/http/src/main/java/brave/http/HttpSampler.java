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
import brave.internal.Nullable;
import brave.sampler.SamplerFunction;
import brave.sampler.SamplerFunctions;

import static brave.http.HttpHandler.NULL_SENTINEL;

/**
 * Decides whether to start a new trace based on http request properties such as path.
 *
 * <p>Ex. Here's a sampler that only traces api requests
 * <pre>{@code
 * httpTracingBuilder.serverSampler(new HttpSampler() {
 *   @Override public <Req> Boolean trySample(HttpAdapter<Req, ?> adapter, Req request) {
 *     return adapter.path(request).startsWith("/api");
 *   }
 * });
 * }</pre>
 *
 * @see HttpRuleSampler
 * @see SamplerFunction
 * @deprecated Since 5.8, use {@code SamplerFunction<HttpRequest>}.
 */
@Deprecated
// abstract class as you can't lambda generic methods anyway. This lets us make helpers in the future
public abstract class HttpSampler implements SamplerFunction<HttpRequest> {
  /** Ignores the request and uses the {@link brave.sampler.Sampler trace ID instead}. */
  public static final HttpSampler TRACE_ID = new HttpSampler() {
    @Override public Boolean trySample(HttpRequest request) {
      return null;
    }

    @Override @Nullable public <Req> Boolean trySample(HttpAdapter<Req, ?> adapter, Req request) {
      return null;
    }

    @Override public String toString() {
      return "DeferDecision";
    }
  };

  /**
   * Returns false to never start new traces for http requests. For example, you may wish to only
   * capture traces if they originated from an inbound server request. Such a policy would filter
   * out client requests made during bootstrap.
   */
  public static final HttpSampler NEVER_SAMPLE = new HttpSampler() {
    @Override public Boolean trySample(HttpRequest request) {
      return false;
    }

    @Override public <Req> Boolean trySample(HttpAdapter<Req, ?> adapter, Req request) {
      return false;
    }

    @Override public String toString() {
      return "NeverSample";
    }
  };

  @Override @Nullable public Boolean trySample(HttpRequest request) {
    if (request == null) return null;

    Object unwrapped = request.unwrap();
    if (unwrapped == null) unwrapped = NULL_SENTINEL; // Ensure adapter methods never see null

    HttpAdapter<Object, Void> adapter;
    if (request instanceof HttpClientRequest) {
      adapter = new HttpClientAdapters.ToRequestAdapter((HttpClientRequest) request, unwrapped);
    } else {
      adapter = new HttpServerAdapters.ToRequestAdapter((HttpServerRequest) request, unwrapped);
    }

    return trySample(adapter, unwrapped);
  }

  /**
   * Returns an overriding sampling decision for a new trace. Return null ignore the request and use
   * the {@link brave.sampler.Sampler trace ID sampler}.
   */
  @Nullable public abstract <Req> Boolean trySample(HttpAdapter<Req, ?> adapter, Req request);

  static HttpSampler fromHttpRequestSampler(SamplerFunction<HttpRequest> sampler) {
    if (sampler == null) throw new NullPointerException("sampler == null");
    if (sampler.equals(SamplerFunctions.deferDecision())) return HttpSampler.TRACE_ID;
    if (sampler.equals(SamplerFunctions.neverSample())) return HttpSampler.NEVER_SAMPLE;
    return sampler instanceof HttpSampler ? (HttpSampler) sampler
      : new HttpRequestSamplerAdapter(sampler);
  }

  static SamplerFunction<HttpRequest> toHttpRequestSampler(SamplerFunction<HttpRequest> sampler) {
    if (sampler == null) throw new NullPointerException("sampler == null");
    if (sampler == HttpSampler.TRACE_ID) return SamplerFunctions.deferDecision();
    if (sampler == HttpSampler.NEVER_SAMPLE) return SamplerFunctions.neverSample();
    return sampler;
  }

  static final class HttpRequestSamplerAdapter extends HttpSampler {
    final SamplerFunction<HttpRequest> delegate;

    HttpRequestSamplerAdapter(SamplerFunction<HttpRequest> delegate) {
      this.delegate = delegate;
    }

    @Override public Boolean trySample(HttpRequest request) {
      return delegate.trySample(request);
    }

    @Override public <Req> Boolean trySample(HttpAdapter<Req, ?> adapter, Req request) {
      if (adapter == null) throw new NullPointerException("adapter == null");
      if (request == null) return null;
      // This can be called independently when interceptors control lifecycle directly. In these
      // cases, it is possible to have an HttpRequest as an argument.
      if (request instanceof HttpRequest) {
        return delegate.trySample((HttpRequest) request);
      }
      return delegate.trySample(new FromHttpAdapter<>(adapter, request));
    }

    @Override public String toString() {
      return delegate.toString();
    }
  }

  @Deprecated static final class FromHttpAdapter<Req> extends HttpRequest {
    final HttpAdapter<Req, ?> adapter;
    final Req request;
    final Span.Kind kind;

    FromHttpAdapter(HttpAdapter<Req, ?> adapter, Req request) {
      if (adapter == null) throw new NullPointerException("adapter == null");
      this.adapter = adapter;
      this.kind = adapter instanceof HttpServerAdapter ? Span.Kind.SERVER : Span.Kind.CLIENT;
      if (request == null) throw new NullPointerException("request == null");
      this.request = request;
    }

    @Override public Span.Kind spanKind() {
      return kind;
    }

    @Override public Object unwrap() {
      return request;
    }

    @Override public String method() {
      return adapter.method(request);
    }

    @Override public String path() {
      return adapter.path(request);
    }

    @Override public String url() {
      return adapter.url(request);
    }

    @Override public String header(String name) {
      return adapter.requestHeader(request, name);
    }

    @Override public String toString() {
      return request.toString();
    }
  }
}

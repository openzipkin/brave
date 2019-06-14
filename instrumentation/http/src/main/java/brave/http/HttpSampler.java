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

import brave.internal.Nullable;
import brave.sampler.Sampler;

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
 */
// abstract class as you can't lambda generic methods anyway. This lets us make helpers in the future
public abstract class HttpSampler {
  /** Ignores the request and uses the {@link brave.sampler.Sampler trace ID instead}. */
  public static final HttpSampler TRACE_ID = new HttpSampler() {
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
    @Override public <Req> Boolean trySample(HttpAdapter<Req, ?> adapter, Req request) {
      return false;
    }

    @Override public String toString() {
      return "NeverSample";
    }
  };

  /**
   * Returns an overriding sampling decision for a new trace. Return null ignore the request and use
   * the {@link brave.sampler.Sampler trace ID sampler}.
   */
  @Nullable public abstract <Req> Boolean trySample(HttpAdapter<Req, ?> adapter, Req request);

  /**
   * Used internally by {@link HttpClientHandler} to override the default sampling decision when
   * starting a new trace for an http client request.
   */
  <Req> Sampler toSampler(HttpAdapter<Req, ?> adapter, Req request, Sampler delegate) {
    if (this == TRACE_ID) return delegate;
    if (this == NEVER_SAMPLE) return Sampler.NEVER_SAMPLE;
    return new Sampler() {
      @Override public boolean isSampled(long traceId) {
        Boolean decision = trySample(adapter, request);
        if (decision == null) return delegate.isSampled(traceId);
        return decision;
      }
    };
  }
}

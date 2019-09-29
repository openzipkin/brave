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

/**
 * Decides whether to start a new trace based on http request properties such as path.
 *
 * <p>Ex. Here's a sampler that only traces api requests
 * <pre>{@code
 * httpTracingBuilder.serverSampler(new HttpSampler() {
 *   @Override public Boolean trySample(HttpRequest request) {
 *     return request.path().startsWith("/api");
 *   }
 * });
 * }</pre>
 *
 * @see HttpRuleSampler
 * @since 5.8
 */
// interface, not abstract type, to allow backporting of HttpSampler.
// This implies we cannot add new methods later, as the bytecode level of Brave core is 1.6
public interface HttpRequestSampler {
  /**
   * Ignores the request and uses the {@link brave.sampler.Sampler trace ID instead}.
   *
   * @since 5.8
   */
  HttpRequestSampler TRACE_ID = new HttpRequestSampler() {
    @Override @Nullable public Boolean trySample(HttpRequest request) {
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
   *
   * @since 5.8
   */
  HttpRequestSampler NEVER_SAMPLE = new HttpRequestSampler() {
    @Override @Nullable public Boolean trySample(HttpRequest request) {
      return false;
    }

    @Override public String toString() {
      return "NeverSample";
    }
  };

  /**
   * Returns an overriding sampling decision for a new trace. Return null ignore the request and use
   * the {@link brave.sampler.Sampler trace ID sampler}.
   *
   * @since 5.8
   */
  @Nullable Boolean trySample(HttpRequest request);
}

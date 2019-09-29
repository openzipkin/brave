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

import brave.sampler.Sampler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static brave.http.HttpRequestMatchers.methodEquals;
import static brave.http.HttpRequestMatchers.pathStartsWith;
import static brave.sampler.Matchers.and;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpRuleSamplerTest {
  @Mock HttpClientAdapter<Object, Object> adapter;
  Object request = new Object();

  @Mock HttpClientRequest httpClientRequest;
  @Mock HttpServerRequest httpServerRequest;

  @Test public void onPath() {
    HttpRuleSampler sampler = HttpRuleSampler.newBuilder()
      .putRule(pathStartsWith("/foo"), Sampler.ALWAYS_SAMPLE)
      .build();

    when(adapter.path(request)).thenReturn("/foo");

    assertThat(sampler.trySample(adapter, request))
      .isTrue();

    when(httpClientRequest.path()).thenReturn("/foo");

    assertThat(sampler.trySample(httpClientRequest))
      .isTrue();

    when(httpServerRequest.path()).thenReturn("/foo");

    assertThat(sampler.trySample(httpServerRequest))
      .isTrue();
  }

  @Test public void onPath_unsampled() {
    HttpRuleSampler sampler = HttpRuleSampler.newBuilder()
      .putRule(pathStartsWith("/foo"), Sampler.NEVER_SAMPLE)
      .build();

    when(adapter.path(request)).thenReturn("/foo");

    assertThat(sampler.trySample(adapter, request))
      .isFalse();
  }

  @Test public void onPath_sampled_prefix() {
    HttpRuleSampler sampler = HttpRuleSampler.newBuilder()
      .putRule(pathStartsWith("/foo"), Sampler.NEVER_SAMPLE)
      .build();

    when(adapter.path(request)).thenReturn("/foo/abcd");

    assertThat(sampler.trySample(adapter, request))
      .isFalse();

    when(httpClientRequest.path()).thenReturn("/foo/abcd");

    assertThat(sampler.trySample(httpClientRequest))
      .isFalse();

    when(httpServerRequest.path()).thenReturn("/foo/abcd");

    assertThat(sampler.trySample(httpServerRequest))
      .isFalse();
  }

  @Test public void onPath_doesntMatch() {
    HttpRuleSampler sampler = HttpRuleSampler.newBuilder()
      .putRule(pathStartsWith("/foo"), Sampler.NEVER_SAMPLE)
      .build();

    when(adapter.path(request)).thenReturn("/bar");

    assertThat(sampler.trySample(adapter, request))
      .isNull();

    when(httpClientRequest.path()).thenReturn("/bar");

    assertThat(sampler.trySample(httpClientRequest))
      .isNull();

    when(httpServerRequest.path()).thenReturn("/bar");

    assertThat(sampler.trySample(httpServerRequest))
      .isNull();
  }

  @Test public void onMethodAndPath_sampled() {
    HttpRuleSampler sampler = HttpRuleSampler.newBuilder()
      .putRule(and(methodEquals("GET"), pathStartsWith("/foo")), Sampler.ALWAYS_SAMPLE)
      .build();

    when(adapter.method(request)).thenReturn("GET");
    when(adapter.path(request)).thenReturn("/foo");

    assertThat(sampler.trySample(adapter, request))
      .isTrue();

    when(httpClientRequest.method()).thenReturn("GET");
    when(httpClientRequest.path()).thenReturn("/foo");

    assertThat(sampler.trySample(httpClientRequest))
      .isTrue();

    when(httpServerRequest.method()).thenReturn("GET");
    when(httpServerRequest.path()).thenReturn("/foo");

    assertThat(sampler.trySample(httpServerRequest))
      .isTrue();
  }

  @Test public void onMethodAndPath_sampled_prefix() {
    HttpRuleSampler sampler = HttpRuleSampler.newBuilder()
      .putRule(and(methodEquals("GET"), pathStartsWith("/foo")), Sampler.ALWAYS_SAMPLE)
      .build();

    when(adapter.method(request)).thenReturn("GET");
    when(adapter.path(request)).thenReturn("/foo/abcd");

    assertThat(sampler.trySample(adapter, request))
      .isTrue();

    when(httpClientRequest.method()).thenReturn("GET");
    when(httpClientRequest.path()).thenReturn("/foo/abcd");

    assertThat(sampler.trySample(httpClientRequest))
      .isTrue();

    when(httpServerRequest.method()).thenReturn("GET");
    when(httpServerRequest.path()).thenReturn("/foo/abcd");

    assertThat(sampler.trySample(httpServerRequest))
      .isTrue();
  }

  @Test public void onMethodAndPath_unsampled() {
    HttpRuleSampler sampler = HttpRuleSampler.newBuilder()
      .putRule(and(methodEquals("GET"), pathStartsWith("/foo")), Sampler.NEVER_SAMPLE)
      .build();

    when(adapter.method(request)).thenReturn("GET");
    when(adapter.path(request)).thenReturn("/foo");

    assertThat(sampler.trySample(adapter, request))
      .isFalse();

    when(httpClientRequest.method()).thenReturn("GET");
    when(httpClientRequest.path()).thenReturn("/foo");

    assertThat(sampler.trySample(httpClientRequest))
      .isFalse();

    when(httpServerRequest.method()).thenReturn("GET");
    when(httpServerRequest.path()).thenReturn("/foo");

    assertThat(sampler.trySample(httpServerRequest))
      .isFalse();
  }

  @Test public void onMethodAndPath_doesntMatch_method() {
    HttpRuleSampler sampler = HttpRuleSampler.newBuilder()
      .putRule(and(methodEquals("GET"), pathStartsWith("/foo")), Sampler.NEVER_SAMPLE)
      .build();

    when(adapter.method(request)).thenReturn("POST");

    assertThat(sampler.trySample(adapter, request))
      .isNull();

    when(httpClientRequest.method()).thenReturn("POST");

    assertThat(sampler.trySample(httpClientRequest))
      .isNull();

    when(httpServerRequest.method()).thenReturn("POST");

    assertThat(sampler.trySample(httpServerRequest))
      .isNull();
  }

  @Test public void onMethodAndPath_doesntMatch_path() {
    HttpRuleSampler sampler = HttpRuleSampler.newBuilder()
      .putRule(and(methodEquals("GET"), pathStartsWith("/foo")), Sampler.NEVER_SAMPLE)
      .build();

    when(adapter.method(request)).thenReturn("GET");
    when(adapter.path(request)).thenReturn("/bar");

    assertThat(sampler.trySample(adapter, request))
      .isNull();

    when(httpClientRequest.method()).thenReturn("GET");
    when(httpClientRequest.path()).thenReturn("/bar");

    assertThat(sampler.trySample(httpClientRequest))
      .isNull();

    when(httpServerRequest.method()).thenReturn("GET");
    when(httpServerRequest.path()).thenReturn("/bar");

    assertThat(sampler.trySample(httpServerRequest))
      .isNull();
  }

  @Test public void nullOnParseFailure() {
    HttpRuleSampler sampler = HttpRuleSampler.newBuilder()
      .putRule(and(methodEquals("GET"), pathStartsWith("/foo")), Sampler.NEVER_SAMPLE)
      .build();

    // not setting up mocks means they return null which is like a parse fail
    assertThat(sampler.trySample(adapter, request))
      .isNull();
    assertThat(sampler.trySample(httpClientRequest))
      .isNull();
    assertThat(sampler.trySample(httpServerRequest))
      .isNull();
  }

  @Test public void putAllRules() {
    HttpRuleSampler base = HttpRuleSampler.newBuilder()
      .putRule(and(methodEquals("GET"), pathStartsWith("/foo")), Sampler.NEVER_SAMPLE)
      .build();

    HttpRuleSampler extended = HttpRuleSampler.newBuilder()
      .putAllRules(base)
      .build();

    when(httpServerRequest.method()).thenReturn("POST");

    assertThat(extended.trySample(httpServerRequest))
      .isNull();

    when(httpServerRequest.method()).thenReturn("GET");
    when(httpServerRequest.path()).thenReturn("/foo");

    assertThat(extended.trySample(httpServerRequest))
      .isFalse();
  }

  // empty may sound unintuitive, but it allows use of the same type when always deferring
  @Test public void noRulesOk() {
    HttpRuleSampler.<Boolean>newBuilder().build();
  }
}

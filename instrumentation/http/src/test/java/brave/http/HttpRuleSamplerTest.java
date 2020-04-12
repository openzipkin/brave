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

import brave.sampler.Matcher;
import brave.sampler.RateLimitingSampler;
import brave.sampler.Sampler;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
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
  @Deprecated @Mock HttpClientAdapter<Object, Object> adapter;
  Object request = new Object();

  @Mock HttpClientRequest httpClientRequest;
  @Mock HttpServerRequest httpServerRequest;

  @Test public void matches() {
    Map<Sampler, Boolean> samplerToAnswer = new LinkedHashMap<>();
    samplerToAnswer.put(Sampler.ALWAYS_SAMPLE, true);
    samplerToAnswer.put(Sampler.NEVER_SAMPLE, false);

    samplerToAnswer.forEach((sampler, answer) -> {
      HttpRuleSampler ruleSampler = HttpRuleSampler.newBuilder()
        .putRule(pathStartsWith("/foo"), sampler)
        .build();

      when(adapter.path(request)).thenReturn("/foo");

      assertThat(ruleSampler.trySample(adapter, request))
        .isEqualTo(answer);

      when(httpClientRequest.path()).thenReturn("/foo");

      assertThat(ruleSampler.trySample(httpClientRequest))
        .isEqualTo(answer);

      when(httpServerRequest.path()).thenReturn("/foo");

      // consistent answer
      assertThat(ruleSampler.trySample(httpServerRequest))
        .isEqualTo(answer);
    });
  }

  @Test public void nullOnNull() {
    HttpRuleSampler ruleSampler = HttpRuleSampler.newBuilder()
      .putRule(pathStartsWith("/bar"), Sampler.ALWAYS_SAMPLE)
      .build();

    assertThat(ruleSampler.trySample(adapter, null))
      .isNull();
    assertThat(ruleSampler.trySample(null))
      .isNull();
  }

  @Test public void unmatched() {
    HttpRuleSampler ruleSampler = HttpRuleSampler.newBuilder()
      .putRule(pathStartsWith("/bar"), Sampler.ALWAYS_SAMPLE)
      .build();

    when(adapter.path(request)).thenReturn("/foo");

    assertThat(ruleSampler.trySample(adapter, request))
      .isNull();

    when(httpClientRequest.path()).thenReturn("/foo");

    assertThat(ruleSampler.trySample(httpClientRequest))
      .isNull();

    when(httpServerRequest.path()).thenReturn("/foo");

    // consistent answer
    assertThat(ruleSampler.trySample(httpServerRequest))
      .isNull();
  }

  @Test public void exampleCustomMatcher() {
    Matcher<HttpRequest> playInTheUSA = request -> {
      if (!"/play".equals(request.path())) return false;
      String url = request.url();
      if (url == null) return false;
      String query = URI.create(url).getQuery();
      return query != null && query.contains("country=US");
    };

    HttpRuleSampler sampler = HttpRuleSampler.newBuilder()
      .putRule(playInTheUSA, RateLimitingSampler.create(100))
      .build();

    when(httpServerRequest.path()).thenReturn("/play");
    when(httpServerRequest.url())
      .thenReturn("https://movies/play?user=gumby&country=US&device=iphone");

    assertThat(sampler.trySample(httpServerRequest))
      .isTrue();

    when(httpServerRequest.path()).thenReturn("/play");
    when(httpServerRequest.url())
      .thenReturn("https://movies/play?user=gumby&country=ES&device=iphone");

    assertThat(sampler.trySample(httpServerRequest))
      .isNull(); // unmatched because country isn't ES
  }

  /** Tests deprecated method */
  @Test public void addRule() {
    HttpRuleSampler sampler = HttpRuleSampler.newBuilder()
      .addRule("GET", "/foo", 0.0f)
      .build();

    when(httpServerRequest.method()).thenReturn("POST");

    assertThat(sampler.trySample(httpServerRequest))
      .isNull();

    when(httpServerRequest.method()).thenReturn("GET");
    when(httpServerRequest.path()).thenReturn("/foo");

    assertThat(sampler.trySample(httpServerRequest))
      .isFalse();
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
    HttpRuleSampler.newBuilder().build();
  }
}

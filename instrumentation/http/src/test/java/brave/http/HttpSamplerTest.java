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

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static brave.http.HttpHandler.NULL_SENTINEL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
@Deprecated public class HttpSamplerTest {
  @Mock HttpClientRequest httpClientRequest;
  @Mock HttpServerRequest httpServerRequest;
  Object request = new Object();

  @Test public void trySample_dispatches() {
    HttpSampler sampler = new HttpSampler() {
      @Override public <Req> Boolean trySample(HttpAdapter<Req, ?> adapter, Req req) {
        return adapter.method(req).equals("POST");
      }
    };

    when(httpClientRequest.method()).thenReturn("POST");
    assertThat(sampler.trySample(httpClientRequest)).isTrue();
    verify(httpClientRequest).method();

    when(httpServerRequest.method()).thenReturn("POST");
    assertThat(sampler.trySample(httpServerRequest)).isTrue();
    verify(httpServerRequest).method();
  }

  @Test public void trySample_seesUnwrappedValue() {
    AtomicBoolean reachedAssertion = new AtomicBoolean();
    HttpSampler sampler = new HttpSampler() {
      @Override public <Req> Boolean trySample(HttpAdapter<Req, ?> adapter, Req req) {
        assertThat(req).isSameAs(request);
        reachedAssertion.set(true);
        return true;
      }
    };

    when(httpClientRequest.unwrap()).thenReturn(request);
    assertThat(sampler.trySample(httpClientRequest)).isTrue();
    assertThat(reachedAssertion.getAndSet(false)).isTrue();

    when(httpServerRequest.unwrap()).thenReturn(request);
    assertThat(sampler.trySample(httpServerRequest)).isTrue();
    assertThat(reachedAssertion.getAndSet(false)).isTrue();
  }

  @Test public void trySample_doesntSeeNullWhenUnwrappedNull() {
    AtomicBoolean reachedAssertion = new AtomicBoolean();
    HttpSampler sampler = new HttpSampler() {
      @Override public <Req> Boolean trySample(HttpAdapter<Req, ?> adapter, Req req) {
        assertThat(req).isSameAs(NULL_SENTINEL);
        reachedAssertion.set(true);
        return true;
      }
    };

    assertThat(sampler.trySample(httpClientRequest)).isTrue();
    assertThat(reachedAssertion.getAndSet(false)).isTrue();

    assertThat(sampler.trySample(httpServerRequest)).isTrue();
    assertThat(reachedAssertion.getAndSet(false)).isTrue();
  }
}

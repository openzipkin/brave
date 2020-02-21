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
import brave.sampler.SamplerFunction;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpServerHandlerTest {
  List<zipkin2.Span> spans = new ArrayList<>();

  @Mock SamplerFunction<HttpRequest> sampler;
  HttpTracing httpTracing;
  HttpServerHandler<HttpServerRequest, HttpServerResponse> handler;

  @Mock(answer = CALLS_REAL_METHODS) HttpServerRequest request;
  @Mock(answer = CALLS_REAL_METHODS) HttpServerResponse response;

  @Before public void init() {
    httpTracing = HttpTracing.newBuilder(Tracing.newBuilder().spanReporter(spans::add).build())
      .serverSampler(sampler).build();
    handler = HttpServerHandler.create(httpTracing);
  }

  @After public void close() {
    Tracing.current().close();
  }

  @Test public void handleReceive_request() {
    // request sampler abstains (trace ID sampler will say true)
    when(sampler.trySample(request)).thenReturn(null);

    Span newSpan = handler.handleReceive(request);
    assertThat(newSpan.isNoop()).isFalse();
    assertThat(newSpan.context().shared()).isFalse();
  }

  @Test public void externalTimestamps() {
    when(sampler.trySample(request)).thenReturn(null);

    when(request.startTimestamp()).thenReturn(123000L);
    when(response.finishTimestamp()).thenReturn(124000L);

    Span span = handler.handleReceive(request);
    handler.handleSend(response, null, span);

    assertThat(spans.get(0).durationAsLong()).isEqualTo(1000L);
  }

  @Test public void handleReceive_samplerSeesHttpRequest() {
    handler.handleReceive(request);

    verify(sampler).trySample(request);
  }

  /** Ensure bad implementation of HttpServerResponse doesn't crash */
  @Test public void handleSend_finishesSpanEvenIfUnwrappedNull() {
    brave.Span span = mock(brave.Span.class);

    handler.handleSend(mock(HttpServerResponse.class), null, span);

    verify(span).isNoop();
    verify(span).context();
    verify(span).customizer();
    verify(span).finish();
    verifyNoMoreInteractions(span);
  }

  /** Ensure bad implementation of HttpServerResponse doesn't crash */
  @Test public void handleSend_finishesSpanEvenIfUnwrappedNull_withError() {
    brave.Span span = mock(brave.Span.class);
    when(span.customizer()).thenReturn(span);

    Exception error = new RuntimeException("peanuts");

    handler.handleSend(mock(HttpServerResponse.class), error, span);

    verify(span).isNoop();
    verify(span).context();
    verify(span).customizer();
    verify(span).error(error);
    verify(span).tag("error", "peanuts");
    verify(span).finish();
    verifyNoMoreInteractions(span);
  }

  @Test public void handleSend_oneOfResponseError() {
    brave.Span span = mock(brave.Span.class);

    assertThatThrownBy(() -> handler.handleSend(null, null, span))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Either the response or error parameters may be null, but not both");
  }
}

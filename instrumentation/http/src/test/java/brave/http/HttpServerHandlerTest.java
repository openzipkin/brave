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
import brave.SpanCustomizer;
import brave.Tracing;
import brave.sampler.Sampler;
import brave.sampler.SamplerFunction;
import brave.sampler.SamplerFunctions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static brave.http.HttpHandler.NULL_SENTINEL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpServerHandlerTest {
  List<zipkin2.Span> spans = new ArrayList<>();

  HttpTracing httpTracing;
  HttpServerHandler<HttpServerRequest, HttpServerResponse> handler;

  @Mock(answer = CALLS_REAL_METHODS) HttpServerRequest request;
  @Mock(answer = CALLS_REAL_METHODS) HttpServerResponse response;

  @Before public void init() {
    init(httpTracingBuilder(tracingBuilder()));
  }

  void init(HttpTracing.Builder builder) {
    close();
    httpTracing = builder.build();
    handler = HttpServerHandler.create(httpTracing);
    when(request.method()).thenReturn("GET");
  }

  HttpTracing.Builder httpTracingBuilder(Tracing.Builder tracingBuilder) {
    return HttpTracing.newBuilder(tracingBuilder.build());
  }

  Tracing.Builder tracingBuilder() {
    return Tracing.newBuilder().spanReporter(spans::add);
  }

  @After public void close() {
    Tracing current = Tracing.current();
    if (current != null) current.close();
  }

  @Test public void handleReceive_traceIdSamplerSpecialCased() {
    Sampler sampler = mock(Sampler.class);

    init(httpTracingBuilder(tracingBuilder().sampler(sampler))
      .serverSampler(SamplerFunctions.deferDecision()));

    assertThat(handler.handleReceive(request).isNoop()).isTrue();

    verify(sampler).isSampled(anyLong());
  }

  @Test public void handleReceive_neverSamplerSpecialCased() {
    Sampler sampler = mock(Sampler.class);

    init(httpTracingBuilder(tracingBuilder().sampler(sampler))
      .serverSampler(SamplerFunctions.neverSample()));

    assertThat(handler.handleReceive(request).isNoop()).isTrue();

    verifyNoMoreInteractions(sampler);
  }

  @Test public void handleReceive_samplerSeesHttpServerRequest() {
    SamplerFunction<HttpRequest> serverSampler = mock(SamplerFunction.class);
    init(httpTracingBuilder(tracingBuilder()).serverSampler(serverSampler));

    handler.handleReceive(request);

    verify(serverSampler).trySample(request);
  }

  @Test public void externalTimestamps() {
    when(request.startTimestamp()).thenReturn(123000L);
    when(response.finishTimestamp()).thenReturn(124000L);

    Span span = handler.handleReceive(request);
    handler.handleSend(response, null, span);

    assertThat(spans.get(0).durationAsLong()).isEqualTo(1000L);
  }

  @Test public void handleSend_finishesSpanEvenIfUnwrappedNull() {
    brave.Span span = mock(brave.Span.class);

    handler.handleSend(mock(HttpServerResponse.class), null, span);

    verify(span).isNoop();
    verify(span).context();
    verify(span).customizer();
    verify(span).finish();
    verifyNoMoreInteractions(span);
  }

  @Test public void handleSend_finishesSpanEvenIfUnwrappedNull_withError() {
    brave.Span span = mock(brave.Span.class);
    when(span.customizer()).thenReturn(span);

    Exception error = new RuntimeException("peanuts");

    handler.handleSend(mock(HttpServerResponse.class), error, span);

    verify(span).isNoop();
    verify(span).context();
    verify(span).customizer();
    verify(span).error(error);
    verify(span).finish();
    verifyNoMoreInteractions(span);
  }

  @Test public void handleSend_oneOfResponseError() {
    brave.Span span = mock(brave.Span.class);

    assertThatThrownBy(() -> handler.handleSend(null, null, span))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Either the response or error parameters may be null, but not both");
  }

  @Test public void handleReceive_oldSamplerDoesntSeeNullWhenUnwrappedNull() {
    AtomicBoolean reachedAssertion = new AtomicBoolean();
    init(httpTracingBuilder(tracingBuilder())
      .serverSampler(new HttpSampler() {
        @Override public <Req> Boolean trySample(HttpAdapter<Req, ?> adapter, Req req) {
          assertThat(req).isSameAs(NULL_SENTINEL);
          reachedAssertion.set(true);
          return true;
        }
      }));

    handler.handleReceive(request);

    assertThat(reachedAssertion).isTrue();
  }

  @Test public void handleReceive_requestParserDoesntSeeNullWhenUnwrappedNull() {
    AtomicBoolean reachedAssertion = new AtomicBoolean();
    init(httpTracingBuilder(tracingBuilder())
      .serverParser(new HttpServerParser() {
        @Override
        public <Req> void request(HttpAdapter<Req, ?> adapter, Req req, SpanCustomizer span) {
          assertThat(req).isSameAs(NULL_SENTINEL);
          reachedAssertion.set(true);
        }
      }));

    handler.handleReceive(request);

    assertThat(reachedAssertion).isTrue();
  }

  @Test public void handleSend_responseParserDoesntSeeNullWhenUnwrappedNull() {
    AtomicBoolean reachedAssertion = new AtomicBoolean();
    init(httpTracingBuilder(tracingBuilder())
      .serverParser(new HttpServerParser() {
        @Override
        public <Resp> void response(HttpAdapter<?, Resp> adapter, Resp resp, Throwable error,
          SpanCustomizer span) {
          assertThat(resp).isSameAs(NULL_SENTINEL);
          reachedAssertion.set(true);
        }
      }));

    handler.handleSend(response, null, mock(brave.Span.class));

    assertThat(reachedAssertion).isTrue();
  }
}

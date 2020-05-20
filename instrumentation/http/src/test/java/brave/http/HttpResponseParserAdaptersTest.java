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

import brave.SpanCustomizer;
import brave.http.HttpResponseParserAdapters.ClientAdapter;
import brave.http.HttpResponseParserAdapters.ServerAdapter;
import brave.propagation.CurrentTraceContext;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.propagation.TraceContext;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

import static brave.http.HttpHandler.NULL_SENTINEL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HttpResponseParserAdaptersTest {
  CurrentTraceContext currentTraceContext = ThreadLocalCurrentTraceContext.create();
  TraceContext context = TraceContext.newBuilder().traceId(1L).spanId(10L).build();
  HttpParser parser = mock(HttpParser.class);
  SpanCustomizer span = mock(SpanCustomizer.class);

  /**
   * The old http handler always parsed in scope because the parser had no argument for a trace
   * context.
   */
  @Test public void parse_parsesInScope() {
    AtomicBoolean parsed = new AtomicBoolean();
    ClientAdapter parserAdapter = new ClientAdapter(currentTraceContext, new HttpParser() {
      @Override
      public <Res> void response(HttpAdapter<?, Res> adapter, Res res, Throwable error,
        SpanCustomizer customizer) {
        parsed.set(true);
        assertThat(currentTraceContext.get()).isSameAs(context);
      }
    });

    parserAdapter.parse(mock(HttpClientResponse.class), context, span);

    assertThat(parsed).isTrue();
  }

  @Test public void parse_HttpClientResponseAdapter() {
    ClientAdapter parserAdapter = new ClientAdapter(currentTraceContext, parser);

    HttpClientAdapter adapter = mock(HttpClientAdapter.class);
    Object res = new Object();

    parserAdapter.parse(new HttpClientAdapters.FromResponseAdapter(adapter, res, null), context,
      span);

    verify(parser).response(adapter, res, null, span);
  }

  @Test public void parse_HttpClientResponse() {
    ClientAdapter parserAdapter = new ClientAdapter(currentTraceContext, parser);

    HttpClientResponse response = mock(HttpClientResponse.class);
    Object res = new Object();
    when(response.unwrap()).thenReturn(res);

    parserAdapter.parse(response, context, span);

    HttpClientAdapters.ToResponseAdapter a =
      new HttpClientAdapters.ToResponseAdapter(response, res);
    verify(parser).response(refEq(a), eq(res), eq(null), eq(span));
  }

  @Test public void parse_HttpClientParse_error() {
    ClientAdapter parserAdapter = new ClientAdapter(currentTraceContext, parser);

    HttpClientResponse response = mock(HttpClientResponse.class);
    Object res = new Object();
    Throwable error = new Throwable();
    when(response.unwrap()).thenReturn(res);
    when(response.error()).thenReturn(error);

    parserAdapter.parse(response, context, span);

    HttpClientAdapters.ToResponseAdapter a =
      new HttpClientAdapters.ToResponseAdapter(response, res);
    verify(parser).response(refEq(a), eq(res), eq(error), eq(span));
  }

  @Test public void parse_HttpClientParse_unwrapNull() {
    ClientAdapter parserAdapter = new ClientAdapter(currentTraceContext, parser);

    HttpClientResponse response = mock(HttpClientResponse.class);
    Object res = NULL_SENTINEL; // to avoid old parsers seeing null

    parserAdapter.parse(response, context, span);

    HttpClientAdapters.ToResponseAdapter a =
      new HttpClientAdapters.ToResponseAdapter(response, res);
    verify(parser).response(refEq(a), eq(res), eq(null), eq(span));
  }

  @Test public void parse_HttpServerResponseAdapter() {
    ServerAdapter parserAdapter = new ServerAdapter(currentTraceContext, parser);

    HttpServerAdapter adapter = mock(HttpServerAdapter.class);
    Object res = new Object();

    parserAdapter.parse(new HttpServerAdapters.FromResponseAdapter(adapter, res, null), context,
      span);

    verify(parser).response(adapter, res, null, span);
  }

  @Test public void parse_HttpServerResponse() {
    ServerAdapter parserAdapter = new ServerAdapter(currentTraceContext, parser);

    HttpServerResponse response = mock(HttpServerResponse.class);
    Object res = new Object();
    when(response.unwrap()).thenReturn(res);

    parserAdapter.parse(response, context, span);

    HttpServerAdapters.ToResponseAdapter a =
      new HttpServerAdapters.ToResponseAdapter(response, res);
    verify(parser).response(refEq(a), eq(res), eq(null), eq(span));
  }

  @Test public void parse_HttpServerParse_unwrapNull() {
    ServerAdapter parserAdapter = new ServerAdapter(currentTraceContext, parser);

    HttpServerResponse response = mock(HttpServerResponse.class);
    Object res = NULL_SENTINEL; // to avoid old parsers seeing null

    parserAdapter.parse(response, context, span);

    HttpServerAdapters.ToResponseAdapter a =
      new HttpServerAdapters.ToResponseAdapter(response, res);
    verify(parser).response(refEq(a), eq(res), eq(null), eq(span));
  }
}

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
import brave.http.HttpRequestParserAdapters.ClientAdapter;
import brave.http.HttpRequestParserAdapters.ServerAdapter;
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

public class HttpRequestParserAdaptersTest {
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
      public <Req> void request(HttpAdapter<Req, ?> adapter, Req req, SpanCustomizer span) {
        parsed.set(true);
        assertThat(currentTraceContext.get()).isSameAs(context);
      }
    });

    parserAdapter.parse(mock(HttpClientRequest.class), context, span);

    assertThat(parsed).isTrue();
  }

  @Test public void parse_HttpClientRequestAdapter() {
    ClientAdapter parserAdapter = new ClientAdapter(currentTraceContext, parser);

    HttpClientAdapter adapter = mock(HttpClientAdapter.class);
    Object req = new Object();

    parserAdapter.parse(new HttpClientAdapters.FromRequestAdapter(adapter, req), context, span);

    verify(parser).request(adapter, req, span);
  }

  @Test public void parse_HttpClientRequest() {
    ClientAdapter parserAdapter = new ClientAdapter(currentTraceContext, parser);

    HttpClientRequest request = mock(HttpClientRequest.class);
    Object req = new Object();
    when(request.unwrap()).thenReturn(req);

    parserAdapter.parse(request, context, span);

    HttpClientAdapters.ToRequestAdapter a = new HttpClientAdapters.ToRequestAdapter(request, req);
    verify(parser).request(refEq(a), eq(req), eq(span));
  }

  @Test public void parse_HttpClientParse_unwrapNull() {
    ClientAdapter parserAdapter = new ClientAdapter(currentTraceContext, parser);

    HttpClientRequest request = mock(HttpClientRequest.class);
    Object req = NULL_SENTINEL; // to avoid old parsers seeing null

    parserAdapter.parse(request, context, span);

    HttpClientAdapters.ToRequestAdapter a = new HttpClientAdapters.ToRequestAdapter(request, req);
    verify(parser).request(refEq(a), eq(req), eq(span));
  }

  @Test public void parse_HttpServerRequestAdapter() {
    ServerAdapter parserAdapter = new ServerAdapter(currentTraceContext, parser);

    HttpServerAdapter adapter = mock(HttpServerAdapter.class);
    Object req = new Object();

    parserAdapter.parse(new HttpServerAdapters.FromRequestAdapter(adapter, req), context, span);

    verify(parser).request(adapter, req, span);
  }

  @Test public void parse_HttpServerRequest() {
    ServerAdapter parserAdapter = new ServerAdapter(currentTraceContext, parser);

    HttpServerRequest request = mock(HttpServerRequest.class);
    Object req = new Object();
    when(request.unwrap()).thenReturn(req);

    parserAdapter.parse(request, context, span);

    HttpServerAdapters.ToRequestAdapter a = new HttpServerAdapters.ToRequestAdapter(request, req);
    verify(parser).request(refEq(a), eq(req), eq(span));
  }

  @Test public void parse_HttpServerParse_unwrapNull() {
    ServerAdapter parserAdapter = new ServerAdapter(currentTraceContext, parser);

    HttpServerRequest request = mock(HttpServerRequest.class);
    Object req = NULL_SENTINEL; // to avoid old parsers seeing null

    parserAdapter.parse(request, context, span);

    HttpServerAdapters.ToRequestAdapter a = new HttpServerAdapters.ToRequestAdapter(request, req);
    verify(parser).request(refEq(a), eq(req), eq(span));
  }
}

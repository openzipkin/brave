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
import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;

import static brave.http.HttpHandler.NULL_SENTINEL;

/** @deprecated Intentionally hidden: implemented to support deprecated signatures. */
@Deprecated final class HttpResponseParserAdapters {
  static final class ClientAdapter extends HttpResponseParserAdapter {
    ClientAdapter(CurrentTraceContext currentTraceContext, HttpParser parser) {
      super(currentTraceContext, parser);
    }

    @Override public void parse(HttpResponse response, TraceContext context, SpanCustomizer span) {
      HttpAdapter<?, Object> adapter;
      Object res;
      if (response instanceof HttpClientAdapters.FromResponseAdapter) { // is a HttpClientResponse
        HttpClientAdapters.FromResponseAdapter wrapped =
          (HttpClientAdapters.FromResponseAdapter) response;
        adapter = wrapped.adapter;
        res = wrapped.response;
      } else if (response instanceof HttpClientResponse) {
        res = response.unwrap();
        if (res == null) res = NULL_SENTINEL; // Ensure adapter methods never see null
        adapter = new HttpClientAdapters.ToResponseAdapter((HttpClientResponse) response, res);
      } else {
        throw new AssertionError("programming bug");
      }
      parseInScope(context, adapter, res, response.error(), span);
    }
  }

  static final class ServerAdapter extends HttpResponseParserAdapter {
    ServerAdapter(CurrentTraceContext currentTraceContext, HttpParser parser) {
      super(currentTraceContext, parser);
    }

    @Override public void parse(HttpResponse response, TraceContext context, SpanCustomizer span) {
      HttpAdapter<?, Object> adapter;
      Object res;
      if (response instanceof HttpServerAdapters.FromResponseAdapter) { // is a HttpServerResponse
        HttpServerAdapters.FromResponseAdapter wrapped =
          (HttpServerAdapters.FromResponseAdapter) response;
        adapter = wrapped.adapter;
        res = wrapped.response;
      } else if (response instanceof HttpServerResponse) {
        res = response.unwrap();
        if (res == null) res = NULL_SENTINEL; // Ensure adapter methods never see null
        adapter = new HttpServerAdapters.ToResponseAdapter((HttpServerResponse) response, res);
      } else {
        throw new AssertionError("programming bug");
      }
      parseInScope(context, adapter, res, response.error(), span);
    }
  }

  static abstract class HttpResponseParserAdapter implements HttpResponseParser {
    final CurrentTraceContext currentTraceContext;
    final HttpParser parser;

    HttpResponseParserAdapter(CurrentTraceContext currentTraceContext, HttpParser parser) {
      this.currentTraceContext = currentTraceContext;
      this.parser = parser;
    }

    <Resp> void parseInScope(TraceContext context, HttpAdapter<?, Resp> adapter, @Nullable Resp res,
      @Nullable Throwable error, SpanCustomizer customizer) {
      Scope ws = currentTraceContext.maybeScope(context);
      try {
        parser.response(adapter, res, error, customizer);
      } finally {
        ws.close();
      }
    }
  }
}

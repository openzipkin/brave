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
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;

import static brave.http.HttpHandler.NULL_SENTINEL;

/** @deprecated Intentionally hidden: implemented to support deprecated signatures. */
@Deprecated final class HttpRequestParserAdapters {
  static final class ClientAdapter extends HttpRequestParserAdapter {
    ClientAdapter(CurrentTraceContext currentTraceContext, HttpParser parser) {
      super(currentTraceContext, parser);
    }

    @Override public void parse(HttpRequest request, TraceContext context, SpanCustomizer span) {
      HttpAdapter<Object, ?> adapter;
      Object req;
      if (request instanceof HttpClientAdapters.FromRequestAdapter) { // is a HttpClientRequest
        HttpClientAdapters.FromRequestAdapter wrapped =
          (HttpClientAdapters.FromRequestAdapter) request;
        adapter = wrapped.adapter;
        req = wrapped.request;
      } else if (request instanceof HttpClientRequest) {
        req = request.unwrap();
        if (req == null) req = NULL_SENTINEL; // Ensure adapter methods never see null
        adapter = new HttpClientAdapters.ToRequestAdapter((HttpClientRequest) request, req);
      } else {
        throw new AssertionError("programming bug");
      }
      parseInScope(context, adapter, req, span);
    }
  }

  static final class ServerAdapter extends HttpRequestParserAdapter {
    ServerAdapter(CurrentTraceContext currentTraceContext, HttpParser parser) {
      super(currentTraceContext, parser);
    }

    @Override public void parse(HttpRequest request, TraceContext context, SpanCustomizer span) {
      HttpAdapter<Object, ?> adapter;
      Object req;
      if (request instanceof HttpServerAdapters.FromRequestAdapter) { // is a HttpServerRequest
        HttpServerAdapters.FromRequestAdapter wrapped =
          (HttpServerAdapters.FromRequestAdapter) request;
        adapter = wrapped.adapter;
        req = wrapped.request;
      } else if (request instanceof HttpServerRequest) {
        req = request.unwrap();
        if (req == null) req = NULL_SENTINEL; // Ensure adapter methods never see null
        adapter = new HttpServerAdapters.ToRequestAdapter((HttpServerRequest) request, req);
      } else {
        throw new AssertionError("programming bug");
      }
      parseInScope(context, adapter, req, span);
    }
  }

  static abstract class HttpRequestParserAdapter implements HttpRequestParser {
    final CurrentTraceContext currentTraceContext;
    final HttpParser parser;

    HttpRequestParserAdapter(CurrentTraceContext currentTraceContext, HttpParser parser) {
      this.currentTraceContext = currentTraceContext;
      this.parser = parser;
    }

    <Req> void parseInScope(TraceContext context, HttpAdapter<Req, ?> adapter, Req req,
      SpanCustomizer span) {
      Scope ws = currentTraceContext.maybeScope(context);
      try {
        parser.request(adapter, req, span);
      } finally {
        ws.close();
      }
    }
  }
}

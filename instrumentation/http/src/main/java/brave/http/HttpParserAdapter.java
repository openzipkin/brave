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

// TODO: adrian hates this type with a passion and will at least separate it into client/server before merge

/** Adapts {@link HttpParser} to the new request and response objects. */
@Deprecated final class HttpParserAdapter implements HttpRequestParser, HttpResponseParser {
  final CurrentTraceContext currentTraceContext;
  final HttpParser parser;

  HttpParserAdapter(CurrentTraceContext currentTraceContext, HttpParser parser) {
    this.currentTraceContext = currentTraceContext;
    this.parser = parser;
  }

  @Override public void parse(HttpRequest request, TraceContext context, SpanCustomizer span) {
    HttpAdapter<Object, ?> adapter;
    Object req;

    // The order matters here because the deprecated HttpClientHandler.create(httpTracing, adapter)
    // can be used even with the HttpRequest type. This means we have to check the HttpRequest type
    // after checking to see if we are in an adapter scenario.
    if (request instanceof HttpClientAdapters.FromRequestAdapter) {
      HttpClientAdapters.FromRequestAdapter wrapped =
        (HttpClientAdapters.FromRequestAdapter) request;
      adapter = wrapped.adapter;
      req = wrapped.request;
    } else if (request instanceof HttpServerAdapters.FromRequestAdapter) {
      HttpServerAdapters.FromRequestAdapter wrapped =
        (HttpServerAdapters.FromRequestAdapter) request;
      adapter = wrapped.adapter;
      req = wrapped.request;
    } else if (request instanceof HttpClientRequest) {
      req = request.unwrap();
      if (req == null) req = NULL_SENTINEL; // Ensure adapter methods never see null
      adapter = new HttpClientAdapters.ToRequestAdapter((HttpClientRequest) request, req);
    } else if (request instanceof HttpServerRequest) {
      req = request.unwrap();
      if (req == null) req = NULL_SENTINEL; // Ensure adapter methods never see null
      adapter = new HttpServerAdapters.ToRequestAdapter((HttpServerRequest) request, req);
    } else {
      throw new AssertionError("programming bug");
    }

    Scope ws = currentTraceContext.maybeScope(context);
    try {
      parser.request(adapter, req, span);
    } finally {
      ws.close();
    }
  }

  @Override public void parse(@Nullable HttpResponse response, @Nullable Throwable error,
    TraceContext context, SpanCustomizer span) {
    HttpAdapter<?, Object> adapter;
    Object resp;

    // The order matters here because the deprecated HttpClientHandler.create(httpTracing, adapter)
    // can be used even with the HttpResponse type. This means we have to check the HttpResponse
    // type after checking to see if we are in an adapter scenario.
    if (response instanceof HttpClientAdapters.FromResponseAdapter) {
      HttpClientAdapters.FromResponseAdapter wrapped =
        (HttpClientAdapters.FromResponseAdapter) response;
      adapter = wrapped.adapter;
      resp = wrapped.response;
    } else if (response instanceof HttpServerAdapters.FromResponseAdapter) {
      HttpServerAdapters.FromResponseAdapter wrapped =
        (HttpServerAdapters.FromResponseAdapter) response;
      adapter = wrapped.adapter;
      resp = wrapped.response;
    } else if (response instanceof HttpClientResponse) {
      resp = response.unwrap();
      if (resp == null) resp = NULL_SENTINEL; // Ensure adapter methods never see null
      adapter = new HttpClientAdapters.ToResponseAdapter((HttpClientResponse) response, resp);
    } else if (response instanceof HttpServerResponse) {
      resp = response.unwrap();
      if (resp == null) resp = NULL_SENTINEL; // Ensure adapter methods never see null
      adapter = new HttpServerAdapters.ToResponseAdapter((HttpServerResponse) response, resp);
    } else {
      throw new AssertionError("programming bug: " + response);
    }

    Scope ws = currentTraceContext.maybeScope(context);
    try {
      parser.response(adapter, resp, error, span);
    } finally {
      ws.close();
    }
  }
}

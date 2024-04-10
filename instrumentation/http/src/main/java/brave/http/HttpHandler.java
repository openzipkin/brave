/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.http;

import brave.Span;
import brave.internal.Platform;

import static brave.internal.Throwables.propagateIfFatal;

abstract class HttpHandler {
  final HttpRequestParser requestParser;
  final HttpResponseParser responseParser;

  HttpHandler(HttpRequestParser requestParser, HttpResponseParser responseParser) {
    this.requestParser = requestParser;
    this.responseParser = responseParser;
  }

  Span handleStart(HttpRequest request, Span span) {
    if (span.isNoop()) return span;

    try {
      parseRequest(request, span);
    } catch (Throwable t) {
      propagateIfFatal(t);
      Platform.get().log("error parsing request {0}", request, t);
    } finally {
      // all of the above parsing happened before a timestamp on the span
      long timestamp = request.startTimestamp();
      if (timestamp == 0L) {
        span.start();
      } else {
        span.start(timestamp);
      }
    }
    return span;
  }

  void parseRequest(HttpRequest request, Span span) {
    span.kind(request.spanKind());
    requestParser.parse(request, span.context(), span.customizer());
  }

  void parseResponse(HttpResponse response, Span span) {
    responseParser.parse(response, span.context(), span.customizer());
  }

  void handleFinish(HttpResponse response, Span span) {
    if (response == null) throw new NullPointerException("response == null");
    if (span == null) throw new NullPointerException("span == null");
    if (span.isNoop()) return;

    if (response.error() != null) {
      span.error(response.error()); // Ensures MutableSpan.error() for SpanHandler
    }

    try {
      parseResponse(response, span);
    } catch (Throwable t) {
      propagateIfFatal(t);
      Platform.get().log("error parsing response {0}", response, t);
    } finally {
      long finishTimestamp = response.finishTimestamp();
      if (finishTimestamp == 0L) {
        span.finish();
      } else {
        span.finish(finishTimestamp);
      }
    }
  }
}

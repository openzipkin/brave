/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.rpc;

import brave.Span;
import brave.internal.Platform;

import static brave.internal.Throwables.propagateIfFatal;

abstract class RpcHandler<Req extends RpcRequest, Resp extends RpcResponse> {
  final RpcRequestParser requestParser;
  final RpcResponseParser responseParser;

  RpcHandler(RpcRequestParser requestParser, RpcResponseParser responseParser) {
    this.requestParser = requestParser;
    this.responseParser = responseParser;
  }

  Span handleStart(Req request, Span span) {
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

  void parseRequest(Req request, Span span) {
    span.kind(request.spanKind());
    request.parseRemoteIpAndPort(span);
    requestParser.parse(request, span.context(), span.customizer());
  }

  void parseResponse(Resp response, Span span) {
    responseParser.parse(response, span.context(), span.customizer());
  }

  void handleFinish(Resp response, Span span) {
    if (response == null) throw new NullPointerException("response == null");
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

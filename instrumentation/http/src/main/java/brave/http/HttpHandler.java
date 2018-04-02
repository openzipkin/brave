package brave.http;

import brave.Span;
import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import zipkin2.Endpoint;

abstract class HttpHandler<Req, Resp, A extends HttpAdapter<Req, Resp>> {

  final CurrentTraceContext currentTraceContext;
  final A adapter;
  final HttpParser parser;

  HttpHandler(CurrentTraceContext currentTraceContext, A adapter, HttpParser parser) {
    this.currentTraceContext = currentTraceContext;
    this.adapter = adapter;
    this.parser = parser;
  }

  Span handleStart(Req request, Span span) {
    if (span.isNoop()) return span;
    Scope ws = maybeNewScope(span, currentTraceContext.get());
    try {
      parser.request(adapter, request, span.customizer());

      Endpoint.Builder remoteEndpoint = Endpoint.newBuilder();
      if (parseRemoteEndpoint(request, remoteEndpoint)) {
        span.remoteEndpoint(remoteEndpoint.build());
      }
    } finally {
      if (ws != null) ws.close();
    }

    // all of the above parsing happened before a timestamp on the span
    return span.start();
  }

  /** Starts a trace scope unless the current is the same span */
  Scope maybeNewScope(Span span, @Nullable TraceContext currentScope) {
    TraceContext spanContext = span.context();
    return spanContext.equals(currentScope) ? null : currentTraceContext.newScope(spanContext);
  }

  /** parses the remote endpoint while the current span is in scope (for logging for example) */
  abstract boolean parseRemoteEndpoint(Req request, Endpoint.Builder remoteEndpoint);

  void handleFinish(@Nullable Resp response, @Nullable Throwable error, Span span) {
    if (span.isNoop()) return;
    TraceContext currentScope = currentTraceContext.get();
    try {
      Scope ws = maybeNewScope(span, currentScope);
      try {
        parser.response(adapter, response, error, span.customizer());
      } finally {
        if (ws != null) ws.close(); // close the scope before finishing the span
      }
    } finally {
      if (currentScope != null) {
        finishInNullScope(span);
      } else {
        span.finish();
      }
    }
  }

  /** Clears the scope to prevent remote reporters from accidentally tracing */
  void finishInNullScope(Span span) {
    Scope ws = currentTraceContext.newScope(null);
    try {
      span.finish();
    } finally {
      ws.close();
    }
  }
}

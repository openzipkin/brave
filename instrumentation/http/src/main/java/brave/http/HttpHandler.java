package brave.http;

import brave.Span;
import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;

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
    Scope ws = currentTraceContext.maybeScope(span.context());
    try {
      parseRequest(request, span);
    } finally {
      ws.close();
    }

    // all of the above parsing happened before a timestamp on the span
    return span.start();
  }

  /** parses remote IP:port and tags while the span is in scope (for logging for example) */
  abstract void parseRequest(Req request, Span span);

  void handleFinish(@Nullable Resp response, @Nullable Throwable error, Span span) {
    if (span.isNoop()) return;
    try {
      Scope ws = currentTraceContext.maybeScope(span.context());
      try {
        parser.response(adapter, response, error, span.customizer());
      } finally {
        ws.close(); // close the scope before finishing the span
      }
    } finally {
      finishInNullScope(span);
    }
  }

  /** Clears the scope to prevent remote reporters from accidentally tracing */
  void finishInNullScope(Span span) {
    Scope ws = currentTraceContext.maybeScope(null);
    try {
      span.finish();
    } finally {
      ws.close();
    }
  }
}

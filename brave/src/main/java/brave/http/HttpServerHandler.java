package brave.http;

import brave.Span;
import zipkin.Constants;

public class HttpServerHandler<Req, Resp> {
  final HttpAdapter<Req, Resp> adapter;
  final HttpServerParser parser;

  public HttpServerHandler(HttpAdapter<Req, Resp> adapter, HttpServerParser parser) {
    this.adapter = adapter;
    this.parser = parser;
  }

  public Req handleReceive(Req request, Span span) {
    if (span.isNoop()) return request;

    // all of the parsing here occur before a timestamp is recorded on the span
    span.kind(Span.Kind.SERVER).name(parser.spanName(adapter, request));
    parser.requestTags(adapter, request, span);
    span.start();
    return request;
  }

  public Resp handleSend(Resp response, Span span) {
    if (span.isNoop()) return response;

    try {
      parser.responseTags(adapter, response, span);
    } finally {
      span.finish();
    }
    return response;
  }

  public <T extends Throwable> T handleError(T throwable, Span span) {
    if (span.isNoop()) return throwable;

    try {
      String message = throwable.getMessage();
      if (message == null) message = throwable.getClass().getSimpleName();
      span.tag(Constants.ERROR, message);
      return throwable;
    } finally {
      span.finish();
    }
  }
}

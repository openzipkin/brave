package brave.http;

import brave.Span;
import zipkin.TraceKeys;

public class HttpServerParser {
  public <Req> String spanName(HttpAdapter<Req, ?> adapter, Req req) {
    return adapter.method(req);
  }

  public <Req> void requestTags(HttpAdapter<Req, ?> adapter, Req req, Span span) {
    span.tag(TraceKeys.HTTP_PATH, adapter.path(req));
  }

  public <Resp> void responseTags(HttpAdapter<?, Resp> adapter, Resp res, Span span) {
    Integer httpStatus = adapter.statusCode(res);
    if (httpStatus != null && (httpStatus < 200 || httpStatus > 299)) {
      span.tag(TraceKeys.HTTP_STATUS_CODE, String.valueOf(httpStatus));
    }
  }
}

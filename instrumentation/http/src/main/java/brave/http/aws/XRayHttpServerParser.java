package brave.http.aws;

import brave.SpanCustomizer;
import brave.http.HttpAdapter;
import brave.http.HttpServerParser;
import zipkin.TraceKeys;

public class XRayHttpServerParser extends HttpServerParser {

  private boolean usePath = false;

  public XRayHttpServerParser(boolean usePath) {
    this.usePath = usePath;
  }

  public XRayHttpServerParser() {
    this(false);
  }

  @Override
  public <Req> void request(HttpAdapter<Req, ?> adapter, Req req, SpanCustomizer customizer) {
    String url = usePath ? adapter.path(req) : adapter.url(req);
    if (url != null) customizer.tag(TraceKeys.HTTP_URL, url);
    super.request(adapter, req, customizer);
  }

}

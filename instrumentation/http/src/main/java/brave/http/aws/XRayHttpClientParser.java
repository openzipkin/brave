package brave.http.aws;

import brave.SpanCustomizer;
import brave.http.HttpAdapter;
import brave.http.HttpServerParser;
import zipkin.TraceKeys;

public class XRayHttpClientParser extends HttpServerParser {

  private boolean usePath = false;

  public XRayHttpClientParser(boolean usePath) {
    this.usePath = usePath;
  }

  public XRayHttpClientParser() {
    this(false);
  }

  @Override
  public <Req> void request(HttpAdapter<Req, ?> adapter, Req req, SpanCustomizer customizer) {
    String url = usePath ? adapter.path(req) : adapter.url(req);
    if (url != null) customizer.tag(TraceKeys.HTTP_URL, url);
    super.request(adapter, req, customizer);
  }

}

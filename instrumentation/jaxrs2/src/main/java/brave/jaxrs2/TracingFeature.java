package brave.jaxrs2;

import brave.Tracing;
import brave.http.HttpTracing;
import javax.inject.Inject;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;
import javax.ws.rs.ext.Provider;

@Provider
public final class TracingFeature implements Feature {
  public static Feature create(Tracing tracing) {
    return new TracingFeature(HttpTracing.create(tracing));
  }

  public static Feature create(HttpTracing httpTracing) {
    return new TracingFeature(httpTracing);
  }

  final HttpTracing httpTracing;

  @Inject TracingFeature(HttpTracing httpTracing) { // intentionally hidden
    this.httpTracing = httpTracing;
  }

  // TODO: figure out how to deal with when the client or server impl is traced upstream
  // See https://github.com/openzipkin/brave/issues/396
  @Override public boolean configure(FeatureContext context) {
    switch (context.getConfiguration().getRuntimeType()) {
      case CLIENT:
        context.register(new TracingClientFilter(httpTracing));
        break;
      case SERVER:
        context.register(new TracingContainerFilter(httpTracing));
    }
    return true;
  }
}

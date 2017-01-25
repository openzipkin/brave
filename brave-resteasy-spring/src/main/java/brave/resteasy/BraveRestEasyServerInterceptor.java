package brave.resteasy;

import brave.ServerHandler;
import brave.Span;
import brave.Tracer;
import brave.parser.Parser;
import brave.parser.TagsParser;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.List;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.ext.Provider;
import org.jboss.resteasy.annotations.interception.ServerInterceptor;
import org.jboss.resteasy.core.ResourceMethod;
import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.spi.Failure;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.interception.PostProcessInterceptor;
import org.jboss.resteasy.spi.interception.PreProcessInterceptor;
import org.springframework.stereotype.Component;
import zipkin.TraceKeys;

@Component
@Provider
@ServerInterceptor
public class BraveRestEasyServerInterceptor
    implements PostProcessInterceptor, PreProcessInterceptor {

  public static class Config extends ServerHandler.Config<HttpRequest, ServerResponse> {

    @Override protected Parser<HttpRequest, String> spanNameParser() {
      return HttpRequest::getHttpMethod;
    }

    @Override protected TagsParser<HttpRequest> requestTagsParser() {
      return (req, span) -> span.tag(TraceKeys.HTTP_URL, req.getUri().toString());
    }

    @Override protected TagsParser<ServerResponse> responseTagsParser() {
      return (res, span) -> {
        int httpStatus = res.getStatus();
        if (httpStatus < 200 || httpStatus > 299) {
          span.tag(TraceKeys.HTTP_STATUS_CODE, String.valueOf(httpStatus));
        }
      };
    }
  }

  // all this still will be set via builder
  Tracer tracer = Tracer.newBuilder().build(); // add reporter etc
  ServerHandler<HttpRequest, ServerResponse> serverHandler = ServerHandler.create(new Config());
  TraceContext.Extractor<HttpRequest> contextExtractor =
      Propagation.B3_STRING.extractor((carrier, key) -> {
        List<String> headers = carrier.getHttpHeaders().getRequestHeader(key);
        return headers == null || headers.isEmpty() ? null : headers.get(0);
      });

  @Override public ServerResponse preProcess(HttpRequest request, ResourceMethod resourceMethod)
      throws Failure, WebApplicationException {
    TraceContextOrSamplingFlags contextOrFlags = contextExtractor.extract(request);
    Span span = contextOrFlags.context() != null
        ? tracer.joinSpan(contextOrFlags.context())
        : tracer.newTrace(contextOrFlags.samplingFlags());
    try {
      serverHandler.handleReceive(request, span);
      request.setAttribute(ServerHandler.CONTEXT_KEY, span);
    } catch (RuntimeException e) {
      throw serverHandler.handleError(e, span);
    }
    return null;
  }

  @Override public void postProcess(ServerResponse response) {
    // TODO: get the span from the response
    Span span = null;//(Span) response.getAttribute(ServerHandler.CONTEXT_KEY);
    try {
      serverHandler.handleSend(response, span);
    } catch (RuntimeException e) {
      throw serverHandler.handleError(e, span);
    }
  }
}
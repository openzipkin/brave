package brave.jaxrs2;

import brave.ServerHandler;
import brave.Span;
import brave.Tracer;
import brave.parser.Parser;
import brave.parser.TagsParser;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import java.io.IOException;
import javax.annotation.Priority;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.ext.Provider;
import zipkin.TraceKeys;

@Provider
@PreMatching
@Priority(0)
public class BraveJaxRs2ServerFilter implements ContainerRequestFilter,
    ContainerResponseFilter {

  public static class Config
      extends ServerHandler.Config<ContainerRequestContext, ContainerResponseContext> {

    @Override protected Parser<ContainerRequestContext, String> spanNameParser() {
      return ContainerRequestContext::getMethod;
    }

    @Override protected TagsParser<ContainerRequestContext> requestTagsParser() {
      return (req, span) -> span.tag(TraceKeys.HTTP_URL, req.getUriInfo().toString());
    }

    @Override protected TagsParser<ContainerResponseContext> responseTagsParser() {
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
  ServerHandler<ContainerRequestContext, ContainerResponseContext> serverHandler =
      ServerHandler.create(new Config());
  TraceContext.Extractor<ContainerRequestContext> contextExtractor =
      Propagation.B3_STRING.extractor(ContainerRequestContext::getHeaderString);

  @Override public void filter(ContainerRequestContext context) throws IOException {
    TraceContextOrSamplingFlags contextOrFlags = contextExtractor.extract(context);
    Span span = contextOrFlags.context() != null
        ? tracer.joinSpan(contextOrFlags.context())
        : tracer.newTrace(contextOrFlags.samplingFlags());
    try {
      serverHandler.handleReceive(context, span);
      context.setProperty(ServerHandler.CONTEXT_KEY, span);
    } catch (RuntimeException e) {
      throw serverHandler.handleError(e, span);
    }
  }

  @Override
  public void filter(final ContainerRequestContext request, ContainerResponseContext response)
      throws IOException {
    Span span = (Span) request.getProperty(ServerHandler.CONTEXT_KEY);
    try {
      serverHandler.handleSend(response, span);
    } catch (RuntimeException e) {
      throw serverHandler.handleError(e, span);
    }
  }
}
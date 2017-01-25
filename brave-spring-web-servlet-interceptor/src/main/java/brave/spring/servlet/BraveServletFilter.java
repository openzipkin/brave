package brave.spring.servlet;

import brave.ServerHandler;
import brave.Span;
import brave.Tracer;
import brave.parser.Parser;
import brave.parser.TagsParser;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;
import zipkin.TraceKeys;

@Configuration // TODO: actually inject things
public class BraveServletFilter
    extends HandlerInterceptorAdapter { // not final because of @Configuration

  public static class Config extends ServerHandler.Config<HttpServletRequest, HttpServletResponse> {

    @Override protected Parser<HttpServletRequest, String> spanNameParser() {
      return HttpServletRequest::getMethod;
    }

    @Override protected Parser<HttpServletRequest, zipkin.Endpoint> requestAddressParser() {
      return new ClientAddressParser("");
    }

    @Override protected TagsParser<HttpServletRequest> requestTagsParser() {
      return (req, span) -> span.tag(TraceKeys.HTTP_URL, req.getRequestURI());
    }

    @Override protected TagsParser<HttpServletResponse> responseTagsParser() {
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
  ServerHandler<HttpServletRequest, HttpServletResponse> serverHandler =
      ServerHandler.create(new brave.servlet.BraveServletFilter.Config());
  TraceContext.Extractor<HttpServletRequest> contextExtractor =
      Propagation.B3_STRING.extractor(HttpServletRequest::getHeader);

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
      Object handler) {
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
    return true;
  }

  @Override
  public void afterConcurrentHandlingStarted(HttpServletRequest request,
      HttpServletResponse response, Object handler) {
  }

  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
      Object handler, Exception ex) {
    Span span = (Span) request.getAttribute(ServerHandler.CONTEXT_KEY);
    try {
      serverHandler.handleSend(response, span);
    } catch (RuntimeException e) {
      throw serverHandler.handleError(e, span);
    }
  }
}
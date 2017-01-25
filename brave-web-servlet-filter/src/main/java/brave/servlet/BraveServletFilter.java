package brave.servlet;

import brave.ServerHandler;
import brave.Span;
import brave.Tracer;
import brave.parser.Parser;
import brave.parser.TagsParser;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import zipkin.TraceKeys;

public final class BraveServletFilter implements Filter {

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
      ServerHandler.create(new Config());
  TraceContext.Extractor<HttpServletRequest> contextExtractor =
      Propagation.B3_STRING.extractor(HttpServletRequest::getHeader);

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain)
      throws IOException, ServletException {

    String alreadyFilteredAttributeName = getAlreadyFilteredAttributeName();
    boolean hasAlreadyFilteredAttribute =
        request.getAttribute(alreadyFilteredAttributeName) != null;

    if (hasAlreadyFilteredAttribute) {
      // Proceed without invoking this filter...
      filterChain.doFilter(request, response);
      return;
    }

    HttpServletRequest httpRequest = (HttpServletRequest) request;
    TraceContextOrSamplingFlags contextOrFlags = contextExtractor.extract(httpRequest);
    Span span = contextOrFlags.context() != null
        ? tracer.joinSpan(contextOrFlags.context())
        : tracer.newTrace(contextOrFlags.samplingFlags());
    try {
      serverHandler.handleReceive(httpRequest, span);
      filterChain.doFilter(request, response);
      serverHandler.handleSend((HttpServletResponse) response, span);
    } catch (RuntimeException e) {
      throw serverHandler.handleError(e, span);
    }
  }

  @Override public void destroy() {
  }

  // TODO: see if the below stuff from the old filter is pulling its weight
  FilterConfig filterConfig;

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    this.filterConfig = filterConfig;
  }

  private String getAlreadyFilteredAttributeName() {
    String name = getFilterName();
    if (name == null) {
      name = getClass().getName();
    }
    return name + ".FILTERED";
  }

  private final String getFilterName() {
    return (this.filterConfig != null ? this.filterConfig.getFilterName() : null);
  }
}
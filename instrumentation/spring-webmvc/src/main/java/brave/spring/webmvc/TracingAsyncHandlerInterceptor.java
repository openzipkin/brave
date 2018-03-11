package brave.spring.webmvc;

import brave.Tracing;
import brave.http.HttpTracing;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

/**
 * @deprecated Use this instead of {@link SpanCustomizingAsyncHandlerInterceptor} with the servlet
 * filter {@link brave.servlet.TracingFilter}.
 */
@Deprecated
public final class TracingAsyncHandlerInterceptor extends HandlerInterceptorAdapter {
  public static AsyncHandlerInterceptor create(Tracing tracing) {
    return new TracingAsyncHandlerInterceptor(HttpTracing.create(tracing));
  }

  public static AsyncHandlerInterceptor create(HttpTracing httpTracing) {
    return new TracingAsyncHandlerInterceptor(httpTracing);
  }

  final HandlerInterceptor delegate;

  @Autowired TracingAsyncHandlerInterceptor(HttpTracing httpTracing) { // internal
    delegate = TracingHandlerInterceptor.create(httpTracing);
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object o)
      throws Exception {
    return delegate.preHandle(request, response, o);
  }

  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
      Object o, Exception ex) throws Exception {
    delegate.afterCompletion(request, response, o, ex);
  }
}

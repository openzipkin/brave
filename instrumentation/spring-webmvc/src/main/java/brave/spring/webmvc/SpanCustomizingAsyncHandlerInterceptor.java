package brave.spring.webmvc;

import brave.SpanCustomizer;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import static brave.spring.webmvc.SpanCustomizingHandlerInterceptor.setHttpRouteAttribute;

/**
 * Same as {@link SpanCustomizingHandlerInterceptor} except it can be used as both an {@link
 * AsyncHandlerInterceptor} or a normal {@link HandlerInterceptor}.
 */
public final class SpanCustomizingAsyncHandlerInterceptor extends HandlerInterceptorAdapter {
  @Autowired(required = false)
  HandlerParser handlerParser = new HandlerParser();

  SpanCustomizingAsyncHandlerInterceptor() { // hide the ctor so we can change later if needed
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object o) {
    SpanCustomizer span = (SpanCustomizer) request.getAttribute(SpanCustomizer.class.getName());
    if (span != null) {
      setHttpRouteAttribute(request);
      handlerParser.preHandle(request, o, span);
    }
    return true;
  }
}

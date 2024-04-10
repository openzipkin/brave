/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.spring.webmvc;

import brave.SpanCustomizer;
import brave.servlet.TracingFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import static brave.spring.webmvc.SpanCustomizingHandlerInterceptor.setErrorAttribute;
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
    Object span = request.getAttribute(SpanCustomizer.class.getName());
    if (span instanceof SpanCustomizer) handlerParser.preHandle(request, o, (SpanCustomizer) span);
    return true;
  }

  /**
   * Sets the "error" and "http.route" attributes so that the {@link TracingFilter} can read them.
   */
  @Override
  public void afterCompletion(
    HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    Object span = request.getAttribute(SpanCustomizer.class.getName());
    if (span instanceof SpanCustomizer) {
      setErrorAttribute(request, ex);
      setHttpRouteAttribute(request);
    }
  }
}

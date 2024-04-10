/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.spring.webmvc;

import brave.SpanCustomizer;
import brave.internal.Nullable;
import brave.servlet.TracingFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * Adds application-tier data to an existing http span via {@link HandlerParser}. This also sets the
 * request property "http.route" so that it can be used in naming the http span.
 *
 * <p>Use this when you start traces at the servlet layer via {@link brave.servlet.TracingFilter}.
 */
public final class SpanCustomizingHandlerInterceptor implements HandlerInterceptor {
  /** Redefined from HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE added in Spring 3. */
  static final String BEST_MATCHING_PATTERN_ATTRIBUTE =
    "org.springframework.web.servlet.HandlerMapping.bestMatchingPattern";

  @Autowired(required = false)
  HandlerParser handlerParser = new HandlerParser();

  SpanCustomizingHandlerInterceptor() { // hide the ctor so we can change later if needed
  }

  /**
   * Parses the request and sets the "http.route" attribute so that the {@link TracingFilter} can
   * read it.
   */
  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object o) {
    Object span = request.getAttribute(SpanCustomizer.class.getName());
    if (span instanceof SpanCustomizer) {
      setHttpRouteAttribute(request);
      handlerParser.preHandle(request, o, (SpanCustomizer) span);
    }
    return true;
  }

  @Override
  public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
    ModelAndView modelAndView) {
  }

  /** Sets the "error" attribute so that the {@link TracingFilter} can read it. */
  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
    Object handler, Exception ex) {
    Object span = request.getAttribute(SpanCustomizer.class.getName());
    if (span instanceof SpanCustomizer) {
      setErrorAttribute(request, ex);
    }
  }

  /**
   * Sets the "error" attribute if not already set, so that the {@link TracingFilter} can read it.
   */
  static void setErrorAttribute(HttpServletRequest request, @Nullable Exception ex) {
    if (ex != null && request.getAttribute("error") == null) {
      request.setAttribute("error", ex);
    }
  }

  /**
   * Sets the "http.route" attribute from {@link #BEST_MATCHING_PATTERN_ATTRIBUTE} so that the
   * {@link TracingFilter} can read it.
   */
  static void setHttpRouteAttribute(HttpServletRequest request) {
    Object httpRoute = request.getAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE);
    request.setAttribute("http.route", httpRoute != null ? httpRoute.toString() : "");
  }
}

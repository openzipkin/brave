/*
 * Copyright 2013-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.spring.webmvc;

import brave.SpanCustomizer;
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

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object o) {
    SpanCustomizer span = (SpanCustomizer) request.getAttribute(SpanCustomizer.class.getName());
    if (span != null) {
      setHttpRouteAttribute(request);
      handlerParser.preHandle(request, o, span);
    }
    return true;
  }

  @Override
  public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
    ModelAndView modelAndView) {
  }

  @Override public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
    Object handler, Exception ex) {
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

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
import brave.http.HttpTracing;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Spring MVC specific type used to customize traced requests based on the handler.
 *
 * <p>Note: This should not duplicate data added by {@link HttpTracing}. For example, this should
 * not add the tag "http.url".
 *
 * <p>Tagging policy adopted from spring cloud sleuth 1.3.x
 */
public class HandlerParser {
  /** Adds no tags to the span representing the request. */
  public static final HandlerParser NOOP = new HandlerParser() {
    @Override protected void preHandle(HttpServletRequest request, Object handler,
      SpanCustomizer customizer) {
    }
  };

  /** Simple class name that processed the request. ex BookController */
  public static final String CONTROLLER_CLASS = "mvc.controller.class";
  /** Method name that processed the request. ex listOfBooks */
  public static final String CONTROLLER_METHOD = "mvc.controller.method";

  /**
   * Invoked prior to request invocation during {@link HandlerInterceptor#preHandle(HttpServletRequest,
   * HttpServletResponse, Object)}.
   *
   * <p>Adds the tags {@link #CONTROLLER_CLASS} and {@link #CONTROLLER_METHOD}. Override or use
   * {@link #NOOP} to change this behavior.
   */
  protected void preHandle(HttpServletRequest request, Object handler, SpanCustomizer customizer) {
    if (WebMvcRuntime.get().isHandlerMethod(handler)) {
      HandlerMethod handlerMethod = ((HandlerMethod) handler);
      customizer.tag(CONTROLLER_CLASS, handlerMethod.getBeanType().getSimpleName());
      customizer.tag(CONTROLLER_METHOD, handlerMethod.getMethod().getName());
    } else {
      customizer.tag(CONTROLLER_CLASS, handler.getClass().getSimpleName());
    }
  }

  public HandlerParser() { // intentionally public for @Autowired to work without explicit binding
  }
}

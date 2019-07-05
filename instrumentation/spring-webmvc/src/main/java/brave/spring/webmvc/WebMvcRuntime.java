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

import brave.http.HttpTracing;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.web.method.HandlerMethod;

/**
 * Access to Spring WebMvc version-specific features
 *
 * <p>Originally designed by OkHttp team, derived from {@code okhttp3.internal.platform.Platform}
 */
abstract class WebMvcRuntime {
  private static final WebMvcRuntime WEBMVC_RUNTIME = findWebMvcRuntime();

  abstract HttpTracing httpTracing(ApplicationContext ctx);

  abstract boolean isHandlerMethod(Object handler);

  WebMvcRuntime() {
  }

  static WebMvcRuntime get() {
    return WEBMVC_RUNTIME;
  }

  /** Attempt to match the host runtime to a capable Platform implementation. */
  static WebMvcRuntime findWebMvcRuntime() {
    // Find spring-webmvc v3.1 new methods
    try {
      Class.forName("org.springframework.web.method.HandlerMethod");
      return new WebMvc31(); // intentionally doesn't not access the type prior to the above guard
    } catch (ClassNotFoundException e) {
      // pre spring-webmvc v3.1
    }

    // compatible with spring-webmvc 2.5
    return new WebMvc25();
  }

  static final class WebMvc31 extends WebMvcRuntime {
    @Override HttpTracing httpTracing(ApplicationContext ctx) {
      return ctx.getBean(HttpTracing.class);
    }

    @Override boolean isHandlerMethod(Object handler) {
      return handler instanceof HandlerMethod;
    }
  }

  static final class WebMvc25 extends WebMvcRuntime {
    @Override HttpTracing httpTracing(ApplicationContext ctx) {
      // Spring 2.5 does not have a get bean by type interface. To remain compatible, lookup by name
      if (ctx.containsBean("httpTracing")) {
        Object bean = ctx.getBean("httpTracing");
        if (bean instanceof HttpTracing) return (HttpTracing) bean;
      }
      throw new NoSuchBeanDefinitionException(HttpTracing.class, "httpTracing");
    }

    @Override boolean isHandlerMethod(Object handler) {
      return false;
    }
  }
}

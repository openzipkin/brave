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
package brave.spring.web;

import brave.Tracing;
import brave.http.HttpTracing;
import org.junit.After;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;

public class TracingClientHttpRequestInterceptorAutowireTest {

  @Configuration static class HttpTracingConfiguration {
    @Bean HttpTracing httpTracing() {
      return HttpTracing.create(Tracing.newBuilder().build());
    }
  }

  @After public void close() {
    Tracing.current().close();
  }

  @Test public void autowiredWithBeanConfig() {
    AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
    ctx.register(HttpTracingConfiguration.class);
    ctx.register(TracingClientHttpRequestInterceptor.class);
    ctx.refresh();

    ctx.getBean(ClientHttpRequestInterceptor.class);
  }
}

package brave.spring.web;

import brave.Tracing;
import brave.http.HttpTracing;
import org.junit.After;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.ClientHttpRequestInterceptor;

public class TracingClientHttpRequestInterceptorAutowireTest {

  @Configuration static class HttpTracingConfiguration {
    @Bean HttpTracing httpTracing() {
      return HttpTracing.create(Tracing.newBuilder().build());
    }
  }

  // NOTE: while bean configuration via @Import works with Spring 4, it does not with Spring 3
  @Configuration @Import(HttpTracingConfiguration.class)
  static class BeanConfiguration {
    @Bean ClientHttpRequestInterceptor tracingInterceptor(HttpTracing httpTracing) {
      return TracingClientHttpRequestInterceptor.create(httpTracing);
    }
  }

  @Test public void autowiredWithBeanConfig() {
    AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
    ctx.register(BeanConfiguration.class);
    ctx.refresh();

    ctx.getBean(ClientHttpRequestInterceptor.class);
  }

  @After public void close(){
    Tracing.current().close();
  }
}

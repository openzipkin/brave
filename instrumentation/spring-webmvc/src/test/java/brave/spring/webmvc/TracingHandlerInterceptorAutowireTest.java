package brave.spring.webmvc;

import brave.Tracing;
import brave.http.HttpTracing;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.HandlerInterceptor;

public class TracingHandlerInterceptorAutowireTest {

  @Configuration static class HttpTracingConfiguration {
    @Bean HttpTracing httpTracing() {
      return HttpTracing.create(Tracing.newBuilder().build());
    }
  }

  @Configuration @Import({
      HttpTracingConfiguration.class,
      TracingHandlerInterceptor.class
  })
  static class ImportConfiguration {
  }

  @Test public void autowiredWithImportConfig() {
    AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
    ctx.register(ImportConfiguration.class);
    ctx.refresh();

    ctx.getBean(HandlerInterceptor.class);
  }

  @Configuration @Import(HttpTracingConfiguration.class)
  static class BeanConfiguration {
    @Bean HandlerInterceptor tracingInterceptor(HttpTracing httpTracing) {
      return TracingHandlerInterceptor.create(httpTracing);
    }
  }

  @Test public void autowiredWithBeanConfig() {
    AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
    ctx.register(BeanConfiguration.class);
    ctx.refresh();

    ctx.getBean(HandlerInterceptor.class);
  }
}

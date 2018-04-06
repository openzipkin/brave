package brave.spring.webmvc;

import brave.Tracing;
import brave.http.HttpTracing;
import brave.propagation.StrictCurrentTraceContext;
import org.junit.After;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.HandlerInterceptor;
import zipkin2.reporter.Reporter;

public class TracingHandlerInterceptorAutowireTest {

  @Configuration static class HttpTracingConfiguration {
    @Bean HttpTracing httpTracing() {
      return HttpTracing.create(Tracing.newBuilder()
          .currentTraceContext(new StrictCurrentTraceContext())
          .spanReporter(Reporter.NOOP)
          .build());
    }
  }

  @After public void close() {
    Tracing.current().close();
  }

  // NOTE: while bean configuration via @Import works with Spring 4, it does not with Spring 3
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

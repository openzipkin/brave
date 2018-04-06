package brave.spring.web;

import brave.Tracing;
import brave.http.HttpTracing;
import brave.propagation.StrictCurrentTraceContext;
import org.junit.After;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import zipkin2.reporter.Reporter;

public class TracingClientHttpRequestInterceptorAutowireTest {

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

  @Test public void autowiredWithBeanConfig() {
    AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
    ctx.register(HttpTracingConfiguration.class);
    ctx.register(TracingClientHttpRequestInterceptor.class);
    ctx.refresh();

    ctx.getBean(ClientHttpRequestInterceptor.class);
  }
}

package brave.spring.web;

import brave.Tracing;
import brave.http.HttpTracing;
import brave.propagation.StrictCurrentTraceContext;
import org.junit.After;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.RestTemplate;
import zipkin2.reporter.Reporter;

public class TracingClientAutoConfigurationTest {

  @Configuration static class HttpTracingConfiguration {
    @Bean HttpTracing httpTracing() {
      return HttpTracing.create(Tracing.newBuilder()
          .currentTraceContext(new StrictCurrentTraceContext())
          .spanReporter(Reporter.NOOP)
          .build());
    }
  }

  @After
  public void tearDown() {
    Tracing.current().close();
  }

  @Test
  public void restTemplateBeansCreated() {
    AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
    ctx.register(TracingClientAutoConfigurationTest.HttpTracingConfiguration.class);
    ctx.register(TracingClientAutoConfiguration.class);
    ctx.refresh();
    ctx.getBean(RestTemplate.class);
    ctx.getBean(AsyncRestTemplate.class);
  }
}

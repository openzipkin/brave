package brave.spring.webmvc;

import brave.Tracing;
import brave.http.HttpServerBenchmarks;
import brave.propagation.aws.AWSPropagation;
import brave.sampler.Sampler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.util.ImmediateInstanceHandle;
import java.io.IOException;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import zipkin2.reporter.Reporter;

public class WebMvcBenchmarks extends HttpServerBenchmarks {

  @Controller
  public static class HelloController {
    @RequestMapping("/nottraced")
    public ResponseEntity<String> nottraced() throws IOException {
      return ResponseEntity.ok("hello world");
    }

    @RequestMapping("/unsampled")
    public ResponseEntity<String> unsampled() throws IOException {
      return ResponseEntity.ok("hello world");
    }

    @RequestMapping("/traced")
    public ResponseEntity<String> traced() throws IOException {
      return ResponseEntity.ok("hello world");
    }

    @RequestMapping("/traced128")
    public ResponseEntity<String> traced128() throws IOException {
      return ResponseEntity.ok("hello world");
    }
  }

  @Configuration
  @EnableWebMvc
  static class SpringConfig extends WebMvcConfigurerAdapter {
    @Override public void addInterceptors(InterceptorRegistry registry) {
      registry.addInterceptor(TracingHandlerInterceptor.create(
          Tracing.newBuilder().sampler(Sampler.NEVER_SAMPLE).spanReporter(Reporter.NOOP).build()
      )).addPathPatterns("/unsampled");
      registry.addInterceptor(TracingHandlerInterceptor.create(
          Tracing.newBuilder().spanReporter(Reporter.NOOP).build()
      )).addPathPatterns("/traced");
      registry.addInterceptor(TracingHandlerInterceptor.create(
          Tracing.newBuilder().traceId128Bit(true).spanReporter(Reporter.NOOP).build()
      )).addPathPatterns("/traced128");
      registry.addInterceptor(TracingHandlerInterceptor.create(
          Tracing.newBuilder()
              .propagationFactory(AWSPropagation.FACTORY)
              .spanReporter(Reporter.NOOP)
              .build()
      )).addPathPatterns("/tracedaws");
    }
  }

  @Override protected void init(DeploymentInfo servletBuilder) {
    AnnotationConfigWebApplicationContext appContext = new AnnotationConfigWebApplicationContext();
    appContext.register(HelloController.class);
    appContext.register(SpringConfig.class);
    servletBuilder.addServlet(new ServletInfo("DispatcherServlet", DispatcherServlet.class,
        () -> new ImmediateInstanceHandle(new DispatcherServlet(appContext))).addMapping("/*"));
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
        .include(".*" + WebMvcBenchmarks.class.getSimpleName() + ".*")
        .build();

    new Runner(opt).run();
  }
}

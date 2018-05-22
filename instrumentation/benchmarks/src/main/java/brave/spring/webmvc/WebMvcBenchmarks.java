package brave.spring.webmvc;

import brave.http.HttpServerBenchmarks;
import brave.propagation.ExtraFieldPropagation;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.util.ImmediateInstanceHandle;
import java.io.IOException;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

import static brave.servlet.ServletBenchmarks.addFilterMappings;

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

    @RequestMapping("/tracedextra")
    public ResponseEntity<String> tracedextra() throws IOException {
      ExtraFieldPropagation.set("country-code", "FO");
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
    @Autowired
    private SpanCustomizingAsyncHandlerInterceptor tracingInterceptor;

    @Override public void addInterceptors(InterceptorRegistry registry) {
      registry.addInterceptor(tracingInterceptor);
    }
  }

  @Override protected void init(DeploymentInfo servletBuilder) {
    addFilterMappings(servletBuilder);
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

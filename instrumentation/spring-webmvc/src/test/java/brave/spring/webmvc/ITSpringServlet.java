package brave.spring.webmvc;

import brave.http.HttpTracing;
import brave.sampler.Sampler;
import brave.test.http.ITHttp;
import brave.test.http.ServletContainer;
import java.io.IOException;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.internal.http.HttpHeaders;
import okio.Buffer;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.After;
import org.junit.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

// don't look at this class it will be refactored later.
public abstract class ITSpringServlet extends ITHttp {
  OkHttpClient client = new OkHttpClient();
  ServletContainer container = new ServletContainer() {
    @Override public void init(ServletContextHandler handler) {
      ITSpringServlet.this.init(handler);
    }
  };

  abstract List<Class<?>> configurationClasses();

  @Before public void setup() {
    httpTracing = HttpTracing.create(tracingBuilder(Sampler.ALWAYS_SAMPLE).build());
    init();
  }

  /** recreates the server so that it uses the supplied trace configuration */
  final void init() {
    container.init();
  }

  protected final String url(String path) {
    return container.url(path);
  }

  @After
  public void stop() {
    container.stop();
  }

  @Configuration
  @EnableWebMvc
  static class AsyncHandlerInterceptorConfig extends WebMvcConfigurerAdapter {
    @Bean AsyncHandlerInterceptor tracingInterceptor(HttpTracing httpTracing) {
      return TracingAsyncHandlerInterceptor.create(httpTracing);
    }

    @Autowired
    private AsyncHandlerInterceptor tracingInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
      registry.addInterceptor(tracingInterceptor);
    }
  }

  void init(ServletContextHandler handler) {
    AnnotationConfigWebApplicationContext appContext =
        new AnnotationConfigWebApplicationContext() {
          // overriding this allows us to register dependencies of TracingHandlerInterceptor
          // without passing static state to a configuration class.
          @Override protected void loadBeanDefinitions(DefaultListableBeanFactory beanFactory) {
            beanFactory.registerSingleton("httpTracing", httpTracing);
            beanFactory.registerSingleton("tracing", httpTracing.tracing());
            beanFactory.registerSingleton("tracer", httpTracing.tracing().tracer());
            super.loadBeanDefinitions(beanFactory);
          }
        };

    for (Class<?> clazz : configurationClasses()) {
      appContext.register(clazz);
    }
    DispatcherServlet servlet = new DispatcherServlet(appContext);
    servlet.setDispatchOptionsRequest(true);
    handler.addServlet(new ServletHolder(servlet), "/*");
  }

  protected Response get(String path) throws IOException {
    return execute(new Request.Builder().url(url(path)).build());
  }

  protected Response execute(Request request) throws IOException {
    try (Response response = client.newCall(request).execute()) {
      if (!HttpHeaders.hasBody(response)) return response;

      // buffer response so tests can read it. Otherwise the finally block will drop it
      ResponseBody toReturn;
      try (ResponseBody body = response.body()) {
        Buffer buffer = new Buffer();
        body.source().readAll(buffer);
        toReturn = ResponseBody.create(body.contentType(), body.contentLength(), buffer);
      }
      return response.newBuilder().body(toReturn).build();
    }
  }
}

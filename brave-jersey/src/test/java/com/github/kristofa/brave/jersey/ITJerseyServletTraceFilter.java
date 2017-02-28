package com.github.kristofa.brave.jersey;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.http.ITHttpServer;
import com.github.kristofa.brave.http.SpanNameProvider;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.servlet.GuiceFilter;
import com.google.inject.servlet.GuiceServletContextListener;
import com.google.inject.servlet.ServletModule;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;
import com.sun.jersey.test.framework.WebAppDescriptor;
import com.sun.jersey.test.framework.spi.container.TestContainer;
import com.sun.jersey.test.framework.spi.container.grizzly2.web.GrizzlyWebTestContainerFactory;
import java.io.IOException;
import java.net.URI;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import okhttp3.HttpUrl;
import org.junit.AfterClass;
import org.junit.BeforeClass;

public class ITJerseyServletTraceFilter extends ITHttpServer {

  static TestContainer server;

  @Path("")
  public static class TestResource {

    @GET
    @Path("foo")
    public Response get() {
      return Response.status(200).build();
    }

    @GET
    @Path("disconnect")
    public Response disconnect() throws IOException {
      throw new IOException();
    }
  }

  public static class GuiceServletConfig extends GuiceServletContextListener {
    @Override protected Injector getInjector() {
      return Guice.createInjector(new ServletModule() {
        @Override
        protected void configureServlets() {
          bind(TestResource.class);
          serve("/*").with(GuiceContainer.class);
          filter("/*").through(new DelegatingServletTraceFilter());
        }
      });
    }
  }

  @BeforeClass public static void startServer() {
    server = new GrizzlyWebTestContainerFactory().create(
        URI.create("http://localhost:9998"),
        new WebAppDescriptor.Builder()
            .contextListenerClass(GuiceServletConfig.class)
            .filterClass(GuiceFilter.class)
            .build()
    );
    server.start();
  }

  @AfterClass public static void stopServer() {
    if (server != null) server.stop();
  }

  @Override protected void init(Brave brave) throws Exception {
    DelegatingServletTraceFilter.setFilter(newTraceFilter(brave, new DefaultSpanNameProvider()));
  }

  @Override protected void init(Brave brave, SpanNameProvider spanNameProvider) throws Exception {
    DelegatingServletTraceFilter.setFilter(newTraceFilter(brave, spanNameProvider));
  }

  ServletTraceFilter newTraceFilter(final Brave brave, final SpanNameProvider spanNameProvider) {
    return Guice.createInjector(new AbstractModule() {
      @Override protected void configure() {
        bind(Brave.class).toInstance(brave);
        bind(SpanNameProvider.class).toInstance(spanNameProvider);
      }
    }).getInstance(ServletTraceFilter.class);
  }

  @Override protected String baseUrl(String path) {
    return HttpUrl.parse(server.getBaseUri().toString()).resolve(path).toString();
  }
}

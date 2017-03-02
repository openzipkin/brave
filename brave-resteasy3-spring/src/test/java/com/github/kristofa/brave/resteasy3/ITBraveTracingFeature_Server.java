package com.github.kristofa.brave.resteasy3;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.http.ITServletContainer;
import com.github.kristofa.brave.http.SpanNameProvider;
import java.io.IOException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.jboss.resteasy.plugins.server.servlet.ResteasyBootstrap;
import org.jboss.resteasy.plugins.spring.SpringContextLoaderListener;
import org.junit.AssumptionViolatedException;
import org.junit.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

public class ITBraveTracingFeature_Server extends ITServletContainer {

  @Override @Test public void reportsSpanOnTransportException() throws Exception {
    // TODO: it seems the only way to process exceptions in a standard way is ExceptionMapper.
    // However, we probably shouldn't do that as it can interfere with user defined ones. We
    // should decide whether to use non-standard means (ex jersey classes), or some other way.
    throw new AssumptionViolatedException("jaxrs-2 filters cannot process exceptions");
  }

  @Override @Test public void reportsClientAddress() {
    throw new AssumptionViolatedException("TODO: fix client address");
  }

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

  @Override
  public void init(ServletContextHandler handler, Brave brave, SpanNameProvider spanNameProvider) {

    AnnotationConfigWebApplicationContext appContext =
        new AnnotationConfigWebApplicationContext() {
          // overriding this allows us to register dependencies of BraveTracingFeatureConfiguration
          // without passing static state to a configuration class.
          @Override protected void loadBeanDefinitions(DefaultListableBeanFactory beanFactory) {
            beanFactory.registerSingleton("brave", brave);
            beanFactory.registerSingleton("spanNameProvider", spanNameProvider);
            super.loadBeanDefinitions(beanFactory);
          }
        };

    appContext.register(TestResource.class); // the test resource
    // TODO: deprecated
    appContext.register(ContainerFiltersConfiguration.class); // generic tracing setup

    // resteasy + spring configuration, programmatically as opposed to using web.xml
    handler.addServlet(new ServletHolder(new HttpServletDispatcher()), "/*");
    handler.addEventListener(new ResteasyBootstrap());
    handler.addEventListener(new SpringContextLoaderListener(appContext));
  }
}

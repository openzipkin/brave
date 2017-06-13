package brave.jaxrs2;

import brave.http.HttpTracing;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import org.jboss.resteasy.plugins.server.servlet.ListenerBootstrap;
import org.jboss.resteasy.plugins.server.servlet.ResteasyBootstrap;
import org.jboss.resteasy.spi.ResteasyConfiguration;
import org.jboss.resteasy.spi.ResteasyDeployment;

public class TracingBootstrap extends ResteasyBootstrap {

  /**
   * {@link ContainerResponseFilter} has no means to handle uncaught exceptions. Unless you provide
   * a catch-all exception mapper, requests that result in unhandled exceptions will leak until they
   * are eventually flushed.
   */
  @Provider
  public static class CatchAllExceptions implements ExceptionMapper<Exception> {

    @Override
    public Response toResponse(Exception e) {
      if (e instanceof WebApplicationException) {
        return ((WebApplicationException) e).getResponse();
      }

      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity("Internal error")
          .type("text/plain")
          .build();
    }
  }

  public TracingBootstrap(HttpTracing httpTracing, Object resource) {
    deployment = new ResteasyDeployment();
    deployment.setApplication(new Application() {
      @Override public Set<Object> getSingletons() {
        return new LinkedHashSet<>(Arrays.asList(
            resource,
            new CatchAllExceptions(),
            new TracingFeature(httpTracing)
        ));
      }
    });
  }

  @Override public void contextInitialized(ServletContextEvent event) {
    ServletContext servletContext = event.getServletContext();
    ListenerBootstrap config = new ListenerBootstrap(servletContext);
    servletContext.setAttribute(ResteasyDeployment.class.getName(), deployment);
    deployment.getDefaultContextObjects().put(ResteasyConfiguration.class, config);
    config.createDeployment();
    deployment.start();
  }
}

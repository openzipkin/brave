package com.github.kristofa.brave.cxf3;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.http.ITHttpServer;
import com.github.kristofa.brave.http.SpanNameProvider;
import java.io.IOException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.junit.After;
import org.junit.AssumptionViolatedException;
import org.junit.Test;

public class ITBraveServerInOutInterceptors extends ITHttpServer {
  @Override @Test public void reportsSpanOnTransportException() throws Exception {
    throw new AssumptionViolatedException("TODO: error tagging");
  }

  @Override @Test public void httpUrlTagIncludesQueryParams() throws Exception {
    throw new AssumptionViolatedException("TODO: add query params to http.url");
  }

  @Override @Test public void reportsClientAddress() {
    throw new AssumptionViolatedException("TODO: fix client address");
  }

  @Override @Test public void addsStatusCodeWhenNotOk() {
    throw new AssumptionViolatedException("TODO: fix error code");
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

  String url = "http://localhost:9000";
  Server server;

  /** recreates the server so that tracing uses the supplied trace configuration */
  @Override protected final void init(Brave brave, SpanNameProvider spanNameProvider) {
    stop();

    // setup server
    JAXRSServerFactoryBean serverFactory = new JAXRSServerFactoryBean();
    serverFactory.setAddress(url);
    serverFactory.setServiceClass(TestResource.class);
    serverFactory.setServiceBean(new TestResource());
    serverFactory.getInInterceptors().add(
        BraveServerInInterceptor.builder(brave).spanNameProvider(spanNameProvider).build()
    );
    serverFactory.getOutInterceptors().add(BraveServerOutInterceptor.create(brave));

    try {
      server = serverFactory.create();
      server.start();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to start server.", e);
    }
  }

  @Override protected final String url(String path) {
    return url + path;
  }

  @After
  public void stop() {
    if (server == null) return;
    try {
      server.stop();
      server = null;
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}

package com.github.kristofa.brave.cxf3;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.LocalTracer;
import com.github.kristofa.brave.http.ITHttpServer;
import com.github.kristofa.brave.http.SpanNameProvider;
import java.io.IOException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Response;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.junit.After;
import org.junit.AssumptionViolatedException;
import org.junit.Test;

public class ITBraveServerInOutInterceptors extends ITHttpServer {
  @Override @Test public void reportsSpanOnTransportException() throws Exception {
    throw new AssumptionViolatedException("TODO: unhandled synchronous exception mapping");
  }

  @Override @Test public void addsErrorTagOnTransportException() throws Exception {
    throw new AssumptionViolatedException("TODO: error tagging");
  }

  @Override @Test public void addsErrorTagOnTransportException_async() throws Exception {
    throw new AssumptionViolatedException("TODO: error tagging");
  }

  @Override @Test public void httpUrlTagIncludesQueryParams() throws Exception {
    throw new AssumptionViolatedException("TODO: add query params to http.url");
  }

  // AbstractHTTPDestination.HTTP_REQUEST can get access to the servlet request, but requires
  // a dep on cxf-rt-transports-http
  @Override @Test public void reportsClientAddress() {
    throw new AssumptionViolatedException("cannot read client address w/o cxf-rt-transports-http");
  }

  @Override @Test public void addsStatusCodeWhenNotOk() {
    throw new AssumptionViolatedException("TODO: fix error code");
  }

  @Override @Test public void createsChildSpan() throws Exception {
    // currently, the parent and child are not joining as a part of the same span
    throw new AssumptionViolatedException("https://github.com/openzipkin/brave/pull/304");
  }

  @Override @Test public void createsChildSpan_async() throws Exception {
    // currently, the parent and child are not joining as a part of the same span
    throw new AssumptionViolatedException("https://github.com/openzipkin/brave/pull/304");
  }

  @Path("")
  public static class TestResource {
    final LocalTracer localTracer;

    TestResource(Brave brave) {
      this.localTracer = brave.localTracer();
    }

    @GET
    @Path("foo")
    public Response get() {
      return Response.status(200).build();
    }

    @GET
    @Path("child")
    public Response child() {
      localTracer.startNewSpan("child", "child");
      localTracer.finishSpan();
      return Response.status(200).build();
    }

    @GET
    @Path("childAsync")
    public void childAsync(@Suspended AsyncResponse response) throws IOException {
      new Thread(() -> {
        localTracer.startNewSpan("child", "child");
        localTracer.finishSpan();
        response.resume(Response.status(200).build());
      }).start();
    }

    @GET
    @Path("disconnect")
    public Response disconnect() throws IOException {
      throw new IOException();
    }

    @GET
    @Path("disconnectAsync")
    public void disconnectAsync(@Suspended AsyncResponse response) throws IOException {
      new Thread(() ->{
        response.resume(new IOException());
      }).start();
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
    serverFactory.setServiceBean(new TestResource(brave));
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

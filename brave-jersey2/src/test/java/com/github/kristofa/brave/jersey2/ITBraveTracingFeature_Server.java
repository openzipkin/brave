package com.github.kristofa.brave.jersey2;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.http.ITHttpServer;
import com.github.kristofa.brave.http.SpanNameProvider;
import com.github.kristofa.brave.jaxrs2.BraveTracingFeature;
import java.io.IOException;
import java.net.URI;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import okhttp3.HttpUrl;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.After;
import org.junit.AssumptionViolatedException;
import org.junit.Test;

public class ITBraveTracingFeature_Server extends ITHttpServer {

  @Override
  @Test
  public void reportsSpanOnTransportException() throws Exception {
    // TODO: it seems the only way to process exceptions in a standard way is ExceptionMapper.
    // However, we probably shouldn't do that as it can interfere with user defined ones. We
    // should decide whether to use non-standard means (ex jersey classes), or some other way.
    throw new AssumptionViolatedException("jaxrs-2 filters cannot process exceptions");
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

  URI baseUri;
  HttpServer server;

  @After public void stopServer() {
    if (server != null) server.stop();
  }

  @Override protected void init(Brave brave) throws Exception {
    newServer(BraveTracingFeature.create(brave));
  }

  @Override protected void init(Brave brave, SpanNameProvider spanNameProvider) throws Exception {
    newServer(BraveTracingFeature.builder(brave).spanNameProvider(spanNameProvider).build());
  }

  void newServer(BraveTracingFeature feature) throws IOException {
    stopServer();
    baseUri = URI.create("http://localhost:9998");
    server = GrizzlyHttpServerFactory.createHttpServer(baseUri, new ResourceConfig()
        .register(feature)
        .register(TestResource.class)
    );
    server.start();
  }

  @Override protected String baseUrl(String path) {
    return HttpUrl.parse(baseUri.toString()).resolve(path).toString();
  }
}

package com.github.kristofa.brave.cxf3.integration;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.BraveExecutorService;
import com.github.kristofa.brave.cxf3.BraveClientFeature;
import com.github.kristofa.brave.cxf3.BraveCxfExecutorService;
import com.github.kristofa.brave.cxf3.BraveJaxRsServerFeature;
import com.github.kristofa.brave.cxf3.ReporterForTesting;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

public class ITBraveCxfInterceptorsJaxrs {

  static String FOO_URL = "http://localhost:9000/test";
  static String BAR_URL = "http://localhost:9001/test";

  Server fooServer;
  Server barServer;
  FooClient fooClient;
  BarClient barClient;
  ReporterForTesting reporter = new ReporterForTesting();

  public interface FooClient {
    @POST
    @Path("/foo")
    String foo();
  }

  public interface BarClient {
    @POST
    @Path("/bar")
    String bar();
  }

  public static class FooService {
    private final BarClient client;
    private final Executor executor;

    public FooService(BarClient client, Executor executor) {
      this.client = client;
      this.executor = executor;
    }

    @POST
    @Path("/foo")
    public void foo(@Suspended AsyncResponse response) {
      executor.execute(() -> {
        response.resume("foo " + client.bar());
      });
    }
  }

  public static class BarService {
    private final Executor executor;

    public BarService(Executor executor) {
      this.executor = executor;
    }

    @POST
    @Path("/bar")
    public void foo(@Suspended AsyncResponse response) {
      executor.execute(() -> {
        response.resume("bar");
      });
    }
  }

  @Before
  public void setUp() throws Exception {

    Brave testFooClient = new Brave.Builder("test-fooClient").reporter(reporter).build();
    Brave fooServerBrave = new Brave.Builder("foo-fooServer").reporter(reporter).build();
    Brave barServerBrave = new Brave.Builder("bar-fooServer").reporter(reporter).build();

    Executor fooServerExecutor = BraveCxfExecutorService.wrap(BraveExecutorService.wrap(Executors.newCachedThreadPool(), fooServerBrave), fooServerBrave);
    Executor barServerExecutor = BraveCxfExecutorService.wrap(BraveExecutorService.wrap(Executors.newCachedThreadPool(), barServerBrave), barServerBrave);

    JAXRSClientFactoryBean fooClientFactory = new JAXRSClientFactoryBean();
    fooClientFactory.setAddress(FOO_URL);
    fooClientFactory.setThreadSafe(true);
    fooClientFactory.setServiceClass(FooClient.class);
    fooClientFactory.getFeatures().add(BraveClientFeature.create(testFooClient));
    this.fooClient = (FooClient) fooClientFactory.create();

    JAXRSClientFactoryBean barClientFactory = new JAXRSClientFactoryBean();
    barClientFactory.setAddress(BAR_URL);
    barClientFactory.setThreadSafe(true);
    barClientFactory.setServiceClass(BarClient.class);
    barClientFactory.getFeatures().add(BraveClientFeature.create(fooServerBrave));
    this.barClient = (BarClient) barClientFactory.create();

    JAXRSServerFactoryBean fooServerFactory = new JAXRSServerFactoryBean();
    fooServerFactory.setAddress(FOO_URL);
    fooServerFactory.setServiceBean(new FooService(barClient, fooServerExecutor));
    fooServerFactory.getFeatures().add(BraveJaxRsServerFeature.create(fooServerBrave));

    this.fooServer = fooServerFactory.create();

    JAXRSServerFactoryBean barServerFactory = new JAXRSServerFactoryBean();
    barServerFactory.setAddress(BAR_URL);
    barServerFactory.setServiceBean(new BarService(barServerExecutor));
    barServerFactory.getFeatures().add(BraveJaxRsServerFeature.create(barServerBrave));
    this.barServer = barServerFactory.create();
  }

  @After
  public void tearDown() throws Exception {
    if (fooServer != null) {
      fooServer.stop();
    }
    if (barServer != null) {
      barServer.stop();
    }
  }

  @Test
  public void testInterceptors() throws Exception {
    String response = fooClient.foo();
    Assert.assertEquals("Response should be 'foo bar'", "foo bar", response);

    assertThat(reporter.getCollectedSpans()).hasSize(4);
    assertEquals(reporter.getCollectedSpans().get(0).traceId, reporter.getCollectedSpans().get(2).traceId);
  }
}
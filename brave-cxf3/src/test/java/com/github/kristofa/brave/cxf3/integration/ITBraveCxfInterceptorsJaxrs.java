package com.github.kristofa.brave.cxf3.integration;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.cxf3.BraveClientInInterceptor;
import com.github.kristofa.brave.cxf3.BraveClientOutInterceptor;
import com.github.kristofa.brave.cxf3.BraveServerInInterceptor;
import com.github.kristofa.brave.cxf3.BraveServerOutInterceptor;
import com.github.kristofa.brave.cxf3.ReporterForTesting;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.client.InvocationCallback;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ITBraveCxfInterceptorsJaxrs {
  Server server;
  Client client;
  ReporterForTesting reporter = new ReporterForTesting();
  Brave brave = new Brave.Builder().reporter(reporter).build();
  BraveServerInInterceptor serverInInterceptor = BraveServerInInterceptor.create(brave);
  BraveServerOutInterceptor serverOutInterceptor = BraveServerOutInterceptor.create(brave);
  BraveClientInInterceptor clientInInterceptor = BraveClientInInterceptor.create(brave);
  BraveClientOutInterceptor clientOutInterceptor = BraveClientOutInterceptor.create(brave);

  public interface Client {
    @POST
    @Path("/foo")
    String foo();
  }

  public static class FooService {
    @POST
    @Path("/foo")
    public void foo(@Suspended AsyncResponse response) {

      Executors.newSingleThreadExecutor().execute(() -> {
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
        }
        response.resume("foo");
      });
    }
  }

  @Before
  public void setUp() throws Exception {
    // setup server
    String url = "http://localhost:9000/test";
    JAXRSServerFactoryBean serverFactory = new JAXRSServerFactoryBean();
    serverFactory.setAddress(url);
    serverFactory.setServiceClass(FooService.class);
    serverFactory.setServiceBean(new FooService());
    serverFactory.getInInterceptors().add(serverInInterceptor);
    serverFactory.getOutInterceptors().add(serverOutInterceptor);
    this.server = serverFactory.create();

    // setup client
    JAXRSClientFactoryBean clientFactory = new JAXRSClientFactoryBean();
    clientFactory.setAddress(url);
    clientFactory.setThreadSafe(true);
    clientFactory.setServiceClass(Client.class);
    clientFactory.getInInterceptors().add(clientInInterceptor);
    clientFactory.getOutInterceptors().add(clientOutInterceptor);
    this.client = (Client) clientFactory.create();
  }

  @After
  public void tearDown() throws Exception {
    if (server != null) {
      server.stop();
    }
  }

  @Test
  public void testInterceptors() throws Exception {

    CountDownLatch latch = new CountDownLatch(1);

    InvocationCallback<String> callback = new InvocationCallback<String>() {
      @Override
      public void completed(String response) {
        latch.countDown();
      }

      @Override
      public void failed(Throwable error) {
      }
    };

    WebClient.getConfig(client)
        .getRequestContext()
        .put(InvocationCallback.class.getName(), callback);

    client.foo();

    latch.await(1, TimeUnit.SECONDS);

    assertThat(reporter.getCollectedSpans()).hasSize(2);
  }
}
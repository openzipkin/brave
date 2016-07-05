package com.github.kristofa.brave.cxf3.integration;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.cxf3.BraveClientInInterceptor;
import com.github.kristofa.brave.cxf3.BraveClientOutInterceptor;
import com.github.kristofa.brave.cxf3.BraveServerInInterceptor;
import com.github.kristofa.brave.cxf3.BraveServerOutInterceptor;
import com.github.kristofa.brave.cxf3.ReporterForTesting;
import javax.jws.WebMethod;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ITBraveCxfInterceptorsJws {
  Server server;
  FooService client;
  ReporterForTesting reporter = new ReporterForTesting();
  Brave brave = new Brave.Builder().reporter(reporter).build();
  BraveServerInInterceptor serverInInterceptor = BraveServerInInterceptor.create(brave);
  BraveServerOutInterceptor serverOutInterceptor = BraveServerOutInterceptor.create(brave);
  BraveClientInInterceptor clientInInterceptor = BraveClientInInterceptor.create(brave);
  BraveClientOutInterceptor clientOutInterceptor = BraveClientOutInterceptor.create(brave);

  public interface FooService {
    @WebMethod
    String foo();
  }

  public static class DefaultFooService implements FooService {
    @Override
    public String foo() {
      return "foo";
    }
  }

  @Before
  public void setUp() throws Exception {
    // setup server
    String url = "http://localhost:9000/test";

    JaxWsServerFactoryBean serverFactory = new JaxWsServerFactoryBean();
    serverFactory.setAddress(url);
    serverFactory.setServiceClass(FooService.class);
    serverFactory.setServiceBean(new DefaultFooService());
    serverFactory.getInInterceptors().add(serverInInterceptor);
    serverFactory.getOutInterceptors().add(serverOutInterceptor);
    this.server = serverFactory.create();

    // setup client
    JaxWsProxyFactoryBean clientFactory = new JaxWsProxyFactoryBean();
    clientFactory.setAddress(url);
    clientFactory.setServiceClass(FooService.class);
    clientFactory.getInInterceptors().add(clientInInterceptor);
    clientFactory.getOutInterceptors().add(clientOutInterceptor);
    this.client = (FooService) clientFactory.create();
  }

  @After
  public void tearDown() throws Exception {
    if (server != null) {
      server.stop();
    }
  }

  @Test
  public void testInterceptors() throws Exception {
    client.foo();
    assertThat(reporter.getCollectedSpans()).hasSize(2);
  }
}
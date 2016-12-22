package com.github.kristofa.brave.cxf3.integration;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.cxf3.BraveClientFeature;
import com.github.kristofa.brave.cxf3.BraveServerFeature;
import com.github.kristofa.brave.cxf3.ReporterForTesting;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.jws.WebMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

public class ITBraveCxfInterceptorsJws {
  Server fooServer;
  Server barServer;
  FooService fooClient;
  BarService barClient;

  private ReporterForTesting reporter;

  public interface FooService {
    @WebMethod
    String foo();
  }

  public interface BarService {
    @WebMethod
    String bar();
  }

  public static class DefaultFooService implements FooService {
    private final BarService barClient;

    public DefaultFooService(BarService barClient) {
      this.barClient = barClient;
    }

    @Override
    public String foo() {
      try {
        return "foo " + barClient.bar();
      } catch (Exception e) {
        return "foo " + e.getMessage();
      }
    }
  }

  public static class DefaultBarService implements BarService {

    @Override
    public String bar() {
      throw new RuntimeException("bar");
    }
  }

  @Before
  public void setUp() throws Exception {
    reporter = new ReporterForTesting();

    String fooUrl = "http://localhost:9000/test";
    String barUrl = "http://localhost:9001/test";

    Brave testFooClient = new Brave.Builder("test-fooClient").reporter(reporter).build();
    Brave fooServerBrave = new Brave.Builder("foo-fooServer").reporter(reporter).build();
    Brave barServerBrave = new Brave.Builder("bar-fooServer").reporter(reporter).build();

    JaxWsProxyFactoryBean fooClientFactory = new JaxWsProxyFactoryBean();
    fooClientFactory.setAddress(fooUrl);
    fooClientFactory.setServiceClass(FooService.class);
    fooClientFactory.getFeatures().add(BraveClientFeature.create(testFooClient));
    this.fooClient = (FooService) fooClientFactory.create();

    JaxWsProxyFactoryBean barClientFactory = new JaxWsProxyFactoryBean();
    barClientFactory.setAddress(barUrl);
    barClientFactory.setServiceClass(BarService.class);
    barClientFactory.getFeatures().add(BraveClientFeature.create(fooServerBrave));
    this.barClient = (BarService) barClientFactory.create();

    JaxWsServerFactoryBean fooServerFactory = new JaxWsServerFactoryBean();
    fooServerFactory.setAddress(fooUrl);
    fooServerFactory.setServiceClass(FooService.class);
    fooServerFactory.setServiceBean(new DefaultFooService(barClient));
    fooServerFactory.getFeatures().add(BraveServerFeature.create(fooServerBrave));
    this.fooServer = fooServerFactory.create();

    JaxWsServerFactoryBean barServerFactory = new JaxWsServerFactoryBean();
    barServerFactory.setAddress(barUrl);
    barServerFactory.setServiceClass(BarService.class);
    barServerFactory.setServiceBean(new DefaultBarService());
    barServerFactory.getFeatures().add(BraveServerFeature.create(barServerBrave));
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
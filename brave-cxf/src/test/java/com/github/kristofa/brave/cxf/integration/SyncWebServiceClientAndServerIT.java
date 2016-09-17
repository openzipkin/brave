package com.github.kristofa.brave.cxf.integration;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.cxf.*;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.http.SpanNameProvider;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.jws.WebMethod;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Michał Podsiedzik
 */
public class SyncWebServiceClientAndServerIT {
    private static final String URL = "http://localhost:9000/test";

    private Server server;
    private FooService client;
    private Brave brave;
    private SpanCollectorForTesting collector;

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
        // setup brave
        this.collector = new SpanCollectorForTesting();
        this.brave = new Brave.Builder().spanCollector(collector).build();
        final SpanNameProvider provider = new DefaultSpanNameProvider();

        // setup server interceptors
        final BraveServerInInterceptor serverInInterceptor = new BraveServerInInterceptor(brave, provider);
        final BraveServerOutInterceptor serverOutInterceptor = new BraveServerOutInterceptor(brave);

        // setup client interceptors
        final BraveClientInInterceptor clientInInterceptor = new BraveClientInInterceptor(brave);
        final BraveClientOutInterceptor clientOutInterceptor = new BraveClientOutInterceptor(brave, provider);

        // setup server
        final JaxWsServerFactoryBean serverFactory = new JaxWsServerFactoryBean();
        serverFactory.setAddress(URL);
        serverFactory.setServiceClass(FooService.class);
        serverFactory.setServiceBean(new DefaultFooService());
        serverFactory.getInInterceptors().add(serverInInterceptor);
        serverFactory.getOutInterceptors().add(serverOutInterceptor);
        this.server = serverFactory.create();

        // setup client
        final JaxWsProxyFactoryBean clientFactory = new JaxWsProxyFactoryBean();
        clientFactory.setAddress(URL);
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
        assertThat(collector.getCollectedSpans()).hasSize(2);
    }
}
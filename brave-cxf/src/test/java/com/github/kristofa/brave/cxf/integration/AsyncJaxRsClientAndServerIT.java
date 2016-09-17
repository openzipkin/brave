package com.github.kristofa.brave.cxf.integration;

import static org.assertj.core.api.Assertions.assertThat;

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

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.cxf.BraveClientInInterceptor;
import com.github.kristofa.brave.cxf.BraveClientOutInterceptor;
import com.github.kristofa.brave.cxf.BraveServerInInterceptor;
import com.github.kristofa.brave.cxf.BraveServerOutInterceptor;
import com.github.kristofa.brave.cxf.SpanCollectorForTesting;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.http.SpanNameProvider;

/**
 * @author Michał Podsiedzik
 */
public class AsyncJaxRsClientAndServerIT
{

    private static final String URL = "http://localhost:9000/test";

    private Server server;
    private Client client;
    private Brave brave;
    private SpanCollectorForTesting collector;

    public interface Client {

        @POST
        @Path("/foo")
        String foo();
    }

    public static class FooService {

        @POST
        @Path("/foo")
        public void foo(@Suspended final AsyncResponse response) {

            Executors.newSingleThreadExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    try
                    {
                        Thread.sleep(100);
                    }
                    catch (InterruptedException e)
                    {
                    }
                    response.resume("foo");
                }
            });
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
        final JAXRSServerFactoryBean serverFactory = new JAXRSServerFactoryBean();
        serverFactory.setAddress(URL);
        serverFactory.setServiceClass(FooService.class);
        serverFactory.setServiceBean(new FooService());
        serverFactory.getInInterceptors().add(serverInInterceptor);
        serverFactory.getOutInterceptors().add(serverOutInterceptor);
        this.server = serverFactory.create();

        // setup client
        final JAXRSClientFactoryBean clientFactory = new JAXRSClientFactoryBean();
        clientFactory.setAddress(URL);
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

        final CountDownLatch latch = new CountDownLatch(1);

        final InvocationCallback<String> callback = new InvocationCallback<String>() {
            @Override
            public void completed(String response) {
                latch.countDown();
            }

            @Override
            public void failed(Throwable error) {
            }
        };

        WebClient.getConfig(client).getRequestContext().put(InvocationCallback.class.getName(), callback);

        client.foo();

        latch.await(1, TimeUnit.SECONDS);

        assertThat(collector.getCollectedSpans()).hasSize(2);
    }
}
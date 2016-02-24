package com.github.kristofa.brave.resteasy3;

import com.github.kristofa.brave.*;
import com.github.kristofa.brave.http.ServiceNameProvider;
import com.github.kristofa.brave.http.SpanNameProvider;
import com.github.kristofa.brave.jaxrs2.BraveClientRequestFilter;
import com.github.kristofa.brave.jaxrs2.BraveClientResponseFilter;
import com.twitter.zipkin.gen.Span;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.bio.SocketConnector;
import org.eclipse.jetty.webapp.WebAppContext;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.plugins.providers.RegisterBuiltin;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationContext;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ITBraveResteasy {

    private Server server;
    private ApplicationContext appContext;

    @Before
    public void setup() {
        server = new Server();

        final SocketConnector connector = new SocketConnector();

        connector.setMaxIdleTime(1000 * 60 * 60);
        connector.setPort(8080);
        server.setConnectors(new Connector[] {connector});

        final WebAppContext context = new WebAppContext();
        context.setServer(server);
        context.setContextPath("/BraveRestEasyIntegration");
        context.setWar("src/test/webapp");

        server.setHandler(context);

        try {
            server.start();
        } catch (final Exception e) {
            throw new IllegalStateException("Failed to start server.", e);
        }

        appContext = BraveContextAware.getApplicationContext();
    }

    @After
    public void tearDown() throws Exception {
        server.stop();
        server.join();
    }

    @Test
    public void test() throws IOException, InterruptedException {

        // this initialization only needs to be done once per VM
        RegisterBuiltin.register(ResteasyProviderFactory.getInstance());

        // Create our client. The client will be configured using BraveClientExecutionInterceptor because
        // Spring will scan com.github.kristofa.brave package. This is the package containing our client interceptor
        // in module brave-resteasy-spring-module which is on our class path.
        ServiceNameProvider serviceNameProvider = appContext.getBean(ServiceNameProvider.class);
        SpanNameProvider spanNameProvider = appContext.getBean(SpanNameProvider.class);
        ClientRequestInterceptor clientRequestInterceptor = appContext.getBean(ClientRequestInterceptor.class);
        ClientResponseInterceptor clientResponseInterceptor = appContext.getBean(ClientResponseInterceptor.class);

        final BraveRestEasyResource client =
                new ResteasyClientBuilder().build().target("http://localhost:8080/BraveRestEasyIntegration")
                        .register(new BraveClientRequestFilter(serviceNameProvider, spanNameProvider, clientRequestInterceptor))
                        .register(new BraveClientResponseFilter(serviceNameProvider, spanNameProvider, clientResponseInterceptor))
                        .proxy(BraveRestEasyResource.class);

        @SuppressWarnings("unchecked")
        final Response response = client.a();
        try {
            assertEquals(200, response.getStatus());
            final List<Span> collectedSpans = SpanCollectorForTesting.getInstance().getCollectedSpans();
            assertEquals(2, collectedSpans.size());
            final Span clientSpan = collectedSpans.get(0);
            final Span serverSpan = collectedSpans.get(1);

            assertEquals("Expected trace id's to be equal", clientSpan.getTrace_id(), serverSpan.getTrace_id());
            assertEquals("Expected span id's to be equal", clientSpan.getId(), serverSpan.getId());
            assertEquals("Expected parent span id's to be equal", clientSpan.getParent_id(), serverSpan.getParent_id());
            assertEquals("Span names of client and server should be equal.", clientSpan.getName(), serverSpan.getName());
            assertEquals("Expect 2 annotations.", 2, clientSpan.getAnnotations().size());
            assertEquals("Expect 2 annotations.", 2, serverSpan.getAnnotations().size());
            assertEquals("service name of end points for both client and server annotations should be equal.",
                clientSpan.getAnnotations().get(0).host.service_name,
                serverSpan.getAnnotations().get(0).host.service_name
            );

        } finally {
            response.close();
        }
    }
}

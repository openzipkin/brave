package com.github.kristofa.brave.resteasy3;

import com.github.kristofa.brave.jaxrs2.BraveTracingFeature;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.bio.SocketConnector;
import org.eclipse.jetty.webapp.WebAppContext;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
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
import zipkin.TraceKeys;
import zipkin.storage.QueryRequest;

import static org.assertj.core.api.Assertions.assertThat;

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
        BraveConfig.storage.clear();
    }

    @Test
    public void test() throws IOException, InterruptedException {

        // this initialization only needs to be done once per VM
        RegisterBuiltin.register(ResteasyProviderFactory.getInstance());

        // Create our client. The beans below are configured by scanning
        // com.github.kristofa.brave.resteasy3 in our test web.xml.
        ResteasyClient client = new ResteasyClientBuilder()
            .register(appContext.getBean(BraveTracingFeature.class))
            .build();

        BraveRestEasyResource resouce =
            client.target("http://localhost:8080/BraveRestEasyIntegration")
                .proxy(BraveRestEasyResource.class);

        @SuppressWarnings("unchecked")
        final Response response = resouce.a();
        try {
            assertThat(response.getStatus())
                .isEqualTo(200);

            List<List<zipkin.Span>> traces = BraveConfig.storage.spanStore()
                .getTraces(QueryRequest.builder().build());
            assertThat(traces).hasSize(1);
            assertThat(traces.get(0))
                .withFailMessage("Expected client and server to share ids: " + traces.get(0))
                .hasSize(1);

            zipkin.Span clientServerSpan = traces.get(0).get(0);
            assertThat(clientServerSpan.annotations).extracting(a -> a.value)
                .containsExactly("cs", "sr", "ss", "cr");

            assertThat(clientServerSpan.binaryAnnotations)
                .filteredOn(ba -> ba.key.equals(TraceKeys.HTTP_URL))
                .extracting(ba -> ba.value)
                .withFailMessage("Expected http urls to be same")
                .hasSize(1);
        } finally {
            response.close();
        }
    }
}

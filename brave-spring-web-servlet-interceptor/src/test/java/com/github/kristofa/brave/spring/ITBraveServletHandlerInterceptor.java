package com.github.kristofa.brave.spring;

import com.github.kristofa.brave.IdConversion;
import com.github.kristofa.brave.http.BraveHttpHeaders;
import com.twitter.zipkin.gen.Span;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.bio.SocketConnector;
import org.eclipse.jetty.webapp.WebAppContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

public class ITBraveServletHandlerInterceptor {

    private Server server;

    @Before
    public void setup() {
        server = new Server();

        final SocketConnector connector = new SocketConnector();

        connector.setMaxIdleTime(1000 * 60 * 60);
        connector.setPort(8080);
        server.setConnectors(new Connector[] {connector});

        final WebAppContext context = new WebAppContext();
        context.setServer(server);
        context.setContextPath("/BraveServletInterceptorIntegration");
        context.setWar("src/test/webapp");

        server.setHandler(context);

        try {
            server.start();
        } catch (final Exception e) {
            throw new IllegalStateException("Failed to start server.", e);
        }
    }

    @After
    public void tearDown() throws Exception {
        server.stop();
        server.join();
        SpanCollectorForTesting.getInstance().clear();
    }

    @Test
    public void syncTest() throws IOException, InterruptedException {
        test("http://localhost:8080/BraveServletInterceptorIntegration/brave-servlet-interceptor/ping/sync");

    }

    @Test
    public void asyncTest() throws IOException, InterruptedException {
        test("http://localhost:8080/BraveServletInterceptorIntegration/brave-servlet-interceptor/ping/async");
    }

    private void test(String testControllerUrl) throws IOException {
        URL url = new URL(testControllerUrl);
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setRequestMethod("GET");
        connection.addRequestProperty(BraveHttpHeaders.Sampled.getName(), "1");
        connection.addRequestProperty(BraveHttpHeaders.TraceId.getName(), IdConversion.convertToString(1L));
        connection.addRequestProperty(BraveHttpHeaders.SpanId.getName(), IdConversion.convertToString(2L));
        connection.addRequestProperty(BraveHttpHeaders.ParentSpanId.getName(), IdConversion.convertToString(3L));
        connection.connect();

        try {
            assertEquals(204, connection.getResponseCode());
            final List<Span> collectedSpans = SpanCollectorForTesting.getInstance().getCollectedSpans();
            assertEquals(1, collectedSpans.size());
            final Span serverSpan = collectedSpans.get(0);

            assertEquals("Expected trace id", serverSpan.getTrace_id(), 1L);
            assertEquals("Expected span id", serverSpan.getId(), 2L);
            assertEquals("Expected parent id", serverSpan.getParent_id().longValue(), 3L);
            assertEquals("Span name.", "get", serverSpan.getName());
            assertEquals("Expect 2 annotations.", 2, serverSpan.getAnnotations().size());
            assertEquals("Expected service name.",
                serverSpan.getAnnotations().get(0).host.service_name, "braveservletinterceptorintegration");

            // make sure client address is added!
            assertThat(serverSpan.getBinary_annotations()).filteredOn(b -> b.getKey().equals("ca"))
                .isNotEmpty();
        } finally {
            connection.disconnect();
        }
    }

}

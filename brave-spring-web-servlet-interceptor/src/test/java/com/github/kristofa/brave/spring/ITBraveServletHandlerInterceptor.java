package com.github.kristofa.brave.spring;

import com.github.kristofa.brave.IdConversion;
import com.github.kristofa.brave.http.BraveHttpHeaders;
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
import zipkin.storage.QueryRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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
        BraveConfig.storage.clear();
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
        connection.addRequestProperty(BraveHttpHeaders.ParentSpanId.getName(), IdConversion.convertToString(2L));
        connection.addRequestProperty(BraveHttpHeaders.SpanId.getName(), IdConversion.convertToString(3L));
        connection.connect();

        try {
            assertThat(connection.getResponseCode())
                .isEqualTo(204);

            List<List<zipkin.Span>> traces = BraveConfig.storage.spanStore()
                .getTraces(QueryRequest.builder().build());
            assertThat(traces).hasSize(1);
            assertThat(traces.get(0)).extracting(s -> s.traceId, s -> s.parentId, s-> s.id)
                .withFailMessage("Expected the server to re-use incoming ids: " + traces.get(0))
                .containsExactly(tuple(1L, 2L, 3L))
                .hasSize(1);

            zipkin.Span serverSpan = traces.get(0).get(0);
            assertThat(serverSpan.annotations).extracting(a -> a.value)
                .containsExactly("sr", "ss");

            assertThat(serverSpan.annotations).extracting(a -> a.endpoint.serviceName)
                .allSatisfy(name -> assertThat(name).isEqualTo("braveservletinterceptorintegration"));

            assertThat(serverSpan.binaryAnnotations).filteredOn(ba -> ba.key.equals("ca"))
                .withFailMessage("Expected client address: " + serverSpan)
                .hasSize(1);
        } finally {
            connection.disconnect();
        }
    }

}

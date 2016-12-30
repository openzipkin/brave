package com.github.kristofa.brave.servlet;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.IdConversion;
import com.github.kristofa.brave.http.BraveHttpHeaders;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.bio.SocketConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import zipkin.storage.InMemoryStorage;
import zipkin.storage.QueryRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 *
 * Attention! This Test configured the BraveServletFilter on EnumSet.allOf(DispatcherType.class), to isSampled the
 * once per request guard functionality. In production, please use DispatcherType.REQUEST.
 *
 */
public class ITBraveServletFilter {
    final InMemoryStorage storage = new InMemoryStorage();
    private Server server;

    @Before
    public void setup() {
        Brave brave = new Brave.Builder("BraveServletFilterService")
            .reporter(s -> storage.spanConsumer().accept(Collections.singletonList(s))).build();

        server = new Server();

        final SocketConnector connector = new SocketConnector();

        connector.setMaxIdleTime(1000 * 60 * 60);
        connector.setPort(8080);
        server.setConnectors(new Connector[]{connector});

        final WebAppContext context = new WebAppContext();
        context.setServer(server);
        context.setContextPath("/BraveServletFilter");

        context.addFilter(new FilterHolder(BraveServletFilter.create(brave)), "/*", EnumSet.allOf(DispatcherType.class));
        context.addServlet(new ServletHolder(new ForwardServlet()), "/test");
        context.addServlet(new ServletHolder(new PingServlet()), "/forwardTo");
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
        storage.clear();
    }

    private static class ForwardServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            req.getRequestDispatcher("forwardTo").forward(req, resp);
        }
    }

    private static class PingServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            System.out.println("pong");
        }
    }

    @Test
    public void test() throws IOException {
        URL url = new URL("http://localhost:8080/BraveServletFilter/test");
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setRequestMethod("GET");
        connection.addRequestProperty(BraveHttpHeaders.Sampled.getName(), "1");
        connection.addRequestProperty(BraveHttpHeaders.TraceId.getName(), IdConversion.convertToString(1L));
        connection.addRequestProperty(BraveHttpHeaders.ParentSpanId.getName(), IdConversion.convertToString(2L));
        connection.addRequestProperty(BraveHttpHeaders.SpanId.getName(), IdConversion.convertToString(3L));
        connection.connect();

        try {
            assertThat(connection.getResponseCode())
                .isEqualTo(200);

            List<List<zipkin.Span>> traces = storage.spanStore()
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
                .allSatisfy(name -> assertThat(name).isEqualTo("braveservletfilterservice"));

            assertThat(serverSpan.binaryAnnotations).filteredOn(ba -> ba.key.equals("ca"))
                .withFailMessage("Expected client address: " + serverSpan)
                .hasSize(1);
        } finally {
            connection.disconnect();
        }
    }

}

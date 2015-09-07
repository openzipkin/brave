package com.github.kristofa.brave.servlet;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.IdConversion;
import com.github.kristofa.brave.http.BraveHttpHeaders;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.twitter.zipkin.gen.Span;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.DispatcherType;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.bio.SocketConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.EnumSet;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 *
 * Attention! This Test configured the BraveServletFilter on EnumSet.allOf(DispatcherType.class), to test the
 * once per request guard functionality. In production, please use DispatcherType.REQUEST.
 *
 */
public class ITBraveServletFilter {

    private Server server;

    @Before
    public void setup() {

        Brave brave = new Brave.Builder("BraveServletFilterService").spanCollector(SpanCollectorForTesting.getInstance()).build();

        server = new Server();

        final SocketConnector connector = new SocketConnector();

        connector.setMaxIdleTime(1000 * 60 * 60);
        connector.setPort(8080);
        server.setConnectors(new Connector[]{connector});

        final WebAppContext context = new WebAppContext();
        context.setServer(server);
        context.setContextPath("/BraveServletFilter");

        context.addFilter(new FilterHolder(new BraveServletFilter(brave.serverRequestInterceptor(), brave.serverResponseInterceptor(), new DefaultSpanNameProvider())), "/*", EnumSet.allOf(DispatcherType.class));
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
        SpanCollectorForTesting.getInstance().clear();
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
        connection.addRequestProperty(BraveHttpHeaders.TraceId.getName(), IdConversion.convertToString(1l));
        connection.addRequestProperty(BraveHttpHeaders.SpanId.getName(), IdConversion.convertToString(2l));
        connection.addRequestProperty(BraveHttpHeaders.ParentSpanId.getName(), IdConversion.convertToString(3l));
        connection.connect();

        try {
            assertEquals(200, connection.getResponseCode());
            final List<Span> collectedSpans = SpanCollectorForTesting.getInstance().getCollectedSpans();
            assertEquals(1, collectedSpans.size());
            final Span serverSpan = collectedSpans.get(0);

            assertEquals("Expected trace id", serverSpan.getTrace_id(), 1l);
            assertEquals("Expected span id", serverSpan.getId(), 2l);
            assertEquals("Expected parent id", serverSpan.getParent_id(), 3l);
            assertEquals("Span name.", "GET", serverSpan.getName());
            assertEquals("Expect 2 annotations.", 2, serverSpan.getAnnotations().size());
            assertEquals("Expected service name.", serverSpan.getAnnotations().get(0).getHost()
                    .getService_name(), "BraveServletFilterService");

        } finally {
            connection.disconnect();
        }
    }

}
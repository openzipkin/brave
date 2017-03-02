package com.github.kristofa.brave.servlet;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.http.ITServletContainer;
import com.github.kristofa.brave.http.SpanNameProvider;
import java.io.IOException;
import java.util.EnumSet;
import javax.servlet.DispatcherType;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.AssumptionViolatedException;
import org.junit.Test;

public class ITBraveServletFilter extends ITServletContainer {

  @Override @Test public void reportsSpanOnTransportException() throws Exception {
    throw new AssumptionViolatedException("TODO: fix error reporting");
  }

  @Override @Test public void addsStatusCodeWhenNotOk() throws Exception {
    throw new AssumptionViolatedException("TODO: fix error reporting");
  }

  static class FooServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
      resp.setStatus(200);
    }
  }

  static class DisconnectServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      throw new IOException();
    }
  }

  @Override
  public void init(ServletContextHandler handler, Brave brave, SpanNameProvider spanNameProvider) {
    // add servlets for the test resource
    handler.addServlet(new ServletHolder(new FooServlet()), "/foo");
    handler.addServlet(new ServletHolder(new DisconnectServlet()), "/disconnect");

    // add the trace filter
    BraveServletFilter filter = BraveServletFilter.builder(brave)
        .spanNameProvider(spanNameProvider)
        .build();
    handler.addFilter(new FilterHolder(filter), "/*", EnumSet.of(DispatcherType.REQUEST));
  }
}

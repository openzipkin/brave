package com.github.kristofa.brave.servlet;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.LocalTracer;
import com.github.kristofa.brave.http.ITServletContainer;
import com.github.kristofa.brave.http.SpanNameProvider;
import java.io.IOException;
import java.util.EnumSet;
import javax.servlet.AsyncContext;
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

  @Override @Test public void addsStatusCodeWhenNotOk() throws Exception {
    throw new AssumptionViolatedException("TODO: fix error reporting");
  }

  @Override @Test public void createsChildSpan_async() throws Exception {
    throw new AssumptionViolatedException("TODO: implement async filtering");
  }

  @Override @Test public void addsErrorTagOnTransportException_async() throws Exception {
    throw new AssumptionViolatedException("TODO: implement async filtering");
  }

  @Override @Test public void reportsSpanOnTransportException_async() throws Exception {
    throw new AssumptionViolatedException("TODO: implement async filtering");
  }

  static class FooServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
      resp.setStatus(200);
    }
  }

  static class ChildServlet extends HttpServlet {
    final LocalTracer tracer;

    ChildServlet(LocalTracer tracer) {
      this.tracer = tracer;
    }

    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
      tracer.startNewSpan("child", "child");
      tracer.finishSpan();
      resp.setStatus(200);
    }
  }

  static class ChildAsyncServlet extends HttpServlet {
    final LocalTracer tracer;

    ChildAsyncServlet(LocalTracer tracer) {
      this.tracer = tracer;
    }

    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
      AsyncContext ctx = req.startAsync();
      ctx.start(() -> {
        tracer.startNewSpan("child", "child");
        tracer.finishSpan();
        ctx.complete();
      });
    }
  }

  static class DisconnectServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      throw new IOException(); // null exception message!
    }
  }

  static class DisconnectAsyncServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      AsyncContext ctx = req.startAsync();
      ctx.start(() -> {
        try {
          // TODO: see if there's another way to abend an async servlet
          ((HttpServletResponse) ctx.getResponse()).sendError(500);
        } catch (IOException e) {
        }
        ctx.complete();
      });
    }
  }

  @Override
  public void init(ServletContextHandler handler, Brave brave, SpanNameProvider spanNameProvider) {
    // add servlets for the test resource
    handler.addServlet(new ServletHolder(new FooServlet()), "/foo");
    handler.addServlet(new ServletHolder(new ChildServlet(brave.localTracer())), "/child");
    handler.addServlet(new ServletHolder(new ChildAsyncServlet(brave.localTracer())),
        "/childAsync");
    handler.addServlet(new ServletHolder(new DisconnectServlet()), "/disconnect");
    handler.addServlet(new ServletHolder(new DisconnectAsyncServlet()), "/disconnectAsync");

    // add the trace filter
    BraveServletFilter filter = BraveServletFilter.builder(brave)
        .spanNameProvider(spanNameProvider)
        .build();
    handler.addFilter(new FilterHolder(filter), "/*", EnumSet.of(DispatcherType.REQUEST));
  }
}

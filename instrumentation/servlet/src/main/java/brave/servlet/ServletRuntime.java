package brave.servlet;

import brave.Span;
import brave.http.HttpServerHandler;
import brave.internal.Nullable;
import java.io.IOException;
import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

/**
 * Access to servlet version-specific features
 *
 * <p>Originally designed by OkHttp team, derived from {@code okhttp3.internal.platform.Platform}
 */
abstract class ServletRuntime {
  private static final ServletRuntime SERVLET_RUNTIME = findServletRuntime();

  HttpServletResponse httpResponse(ServletResponse response) {
    return (HttpServletResponse) response;
  }

  abstract @Nullable Integer status(HttpServletResponse response);

  abstract boolean isAsync(HttpServletRequest request);

  abstract void handleAsync(HttpServerHandler<HttpServletRequest, HttpServletResponse> handler,
      HttpServletRequest request, Span span);

  ServletRuntime() {
  }

  static ServletRuntime get() {
    return SERVLET_RUNTIME;
  }

  /** Attempt to match the host runtime to a capable Platform implementation. */
  private static ServletRuntime findServletRuntime() {
    // Find Servlet v3 new methods
    try {
      Class.forName("javax.servlet.AsyncEvent");
      HttpServletRequest.class.getMethod("isAsyncStarted");
      return new Servlet3();
    } catch (NoSuchMethodException e) {
      // pre Servlet v3
    } catch (ClassNotFoundException e) {
      // pre Servlet v3
    }

    // compatible with Servlet 2.5
    return new Servlet25();
  }

  private static final class Servlet3 extends ServletRuntime {
    @Override boolean isAsync(HttpServletRequest request) {
      return request.isAsyncStarted();
    }

    @Override @Nullable Integer status(HttpServletResponse response) {
      return response.getStatus();
    }

    @Override void handleAsync(HttpServerHandler<HttpServletRequest, HttpServletResponse> handler,
        HttpServletRequest request, Span span) {
      if (span.isNoop()) return; // don't add overhead when we aren't httpTracing
      request.getAsyncContext().addListener(new TracingAsyncListener(handler, span));
    }

    static final class TracingAsyncListener implements AsyncListener {
      final HttpServerHandler<HttpServletRequest, HttpServletResponse> handler;
      final Span span;
      volatile boolean complete; // multiple async events can occur, only complete once

      TracingAsyncListener(HttpServerHandler<HttpServletRequest, HttpServletResponse> handler,
          Span span) {
        this.handler = handler;
        this.span = span;
      }

      @Override public void onComplete(AsyncEvent e) {
        if (complete) return;
        handler.handleSend((HttpServletResponse) e.getSuppliedResponse(), null, span);
        complete = true;
      }

      @Override public void onTimeout(AsyncEvent e) {
        if (complete) return;
        span.tag("error", String.format("Timed out after %sms", e.getAsyncContext().getTimeout()));
        handler.handleSend((HttpServletResponse) e.getSuppliedResponse(), null, span);
        complete = true;
      }

      @Override public void onError(AsyncEvent e) {
        if (complete) return;
        handler.handleSend(null, e.getThrowable(), span);
        complete = true;
      }

      /** If another async is created (ex via asyncContext.dispatch), this needs to be re-attached */
      @Override public void onStartAsync(AsyncEvent event) {
        AsyncContext eventAsyncContext = event.getAsyncContext();
        if (eventAsyncContext != null) eventAsyncContext.addListener(this);
      }

      @Override public String toString() {
        return "TracingAsyncListener{" + span.context() + "}";
      }
    }
  }

  private static final class Servlet25 extends ServletRuntime {
    @Override HttpServletResponse httpResponse(ServletResponse response) {
      return new Servlet25ServerResponseAdapter(response);
    }

    @Override boolean isAsync(HttpServletRequest request) {
      return false;
    }

    @Override void handleAsync(HttpServerHandler<HttpServletRequest, HttpServletResponse> handler,
        HttpServletRequest request, Span span) {
      assert false : "this should never be called in Servlet 2.5";
    }

    @Override @Nullable Integer status(HttpServletResponse response) {
      if (response instanceof Servlet25ServerResponseAdapter) {
        // servlet 2.5 doesn't have get status
        return ((Servlet25ServerResponseAdapter) response).getStatusInServlet25();
      }
      return null;
    }
  }

  /** When deployed in Servlet 2.5 environment {@link #getStatus} is not available. */
  static final class Servlet25ServerResponseAdapter extends HttpServletResponseWrapper {
    // The Servlet spec says: calling setStatus is optional, if no status is set, the default is OK.
    int httpStatus = HttpServletResponse.SC_OK;

    Servlet25ServerResponseAdapter(ServletResponse response) {
      super((HttpServletResponse) response);
    }

    @Override public void setStatus(int sc, String sm) {
      httpStatus = sc;
      super.setStatus(sc, sm);
    }

    @Override public void sendError(int sc) throws IOException {
      httpStatus = sc;
      super.sendError(sc);
    }

    @Override public void sendError(int sc, String msg) throws IOException {
      httpStatus = sc;
      super.sendError(sc, msg);
    }

    @Override public void setStatus(int sc) {
      httpStatus = sc;
      super.setStatus(sc);
    }

    public int getStatusInServlet25() {
      return httpStatus;
    }
  }
}

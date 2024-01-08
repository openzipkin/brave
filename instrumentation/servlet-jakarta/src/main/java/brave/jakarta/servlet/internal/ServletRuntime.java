/*
 * Copyright 2013-2024 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.jakarta.servlet.internal;

import brave.Span;
import brave.http.HttpServerHandler;
import brave.http.HttpServerRequest;
import brave.http.HttpServerResponse;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Access to servlet version-specific features
 *
 * <p>Originally designed by OkHttp team, derived from {@code okhttp3.internal.platform.Platform}
 */
public abstract class ServletRuntime {
  private static final ServletRuntime SERVLET_RUNTIME = new Servlet5();

  public HttpServletResponse httpServletResponse(ServletResponse response) {
    return (HttpServletResponse) response;
  }

  /** public for {@link brave.jakarta.servlet.HttpServletResponseWrapper}. */
  public abstract int status(HttpServletResponse response);

  public abstract boolean isAsync(HttpServletRequest request);

  public abstract void handleAsync(
    HttpServerHandler<HttpServerRequest, HttpServerResponse> handler,
    HttpServletRequest request, HttpServletResponse response, Span span);

  ServletRuntime() {
  }

  public static ServletRuntime get() {
    return SERVLET_RUNTIME;
  }

  static final class Servlet5 extends ServletRuntime {
    @Override public boolean isAsync(HttpServletRequest request) {
      return request.isAsyncStarted();
    }

    @Override public int status(HttpServletResponse response) {
      return response.getStatus();
    }

    @Override public void handleAsync(
      HttpServerHandler<HttpServerRequest, HttpServerResponse> handler,
      HttpServletRequest request, HttpServletResponse response, Span span) {
      if (span.isNoop()) return; // don't add overhead when we aren't httpTracing
      TracingAsyncListener listener = new TracingAsyncListener(handler, span);
      request.getAsyncContext().addListener(listener, request, response);
    }

    static final class TracingAsyncListener implements AsyncListener {
      final HttpServerHandler<HttpServerRequest, HttpServerResponse> handler;
      final Span span;

      TracingAsyncListener(HttpServerHandler<HttpServerRequest, HttpServerResponse> handler,
        Span span
      ) {
        this.handler = handler;
        this.span = span;
      }

      @Override public void onComplete(AsyncEvent e) {
        HttpServletRequest req = (HttpServletRequest) e.getSuppliedRequest();
        // Use package-private attribute to check if this hook was called redundantly
        Object sendHandled = req.getAttribute("brave.jakarta.servlet.TracingFilter$SendHandled");
        if (sendHandled instanceof AtomicBoolean
            && ((AtomicBoolean) sendHandled).compareAndSet(false, true)) {
          HttpServletResponse res = (HttpServletResponse) e.getSuppliedResponse();

          HttpServerResponse response =
              brave.jakarta.servlet.HttpServletResponseWrapper.create(req, res, e.getThrowable());
          handler.handleSend(response, span);
        } else {
          // TODO: None of our tests reach this condition. Make a concrete case that re-enters the
          // onComplete hook or remove the special case
        }
      }

      // Per Servlet 3 section 2.3.3.3, we can't see the final HTTP status, yet. defer to onComplete
      // https://download.oracle.com/otndocs/jcp/servlet-3.0-mrel-eval-oth-JSpec/
      @Override public void onTimeout(AsyncEvent e) {
        // Propagate the timeout so that the onComplete hook can see it.
        ServletRequest request = e.getSuppliedRequest();
        if (request.getAttribute("error") == null) {
          request.setAttribute("error", new AsyncTimeoutException(e));
        }
      }

      // Per Servlet 3 section 2.3.3.3, we can't see the final HTTP status, yet. defer to onComplete
      // https://download.oracle.com/otndocs/jcp/servlet-3.0-mrel-eval-oth-JSpec/
      @Override public void onError(AsyncEvent e) {
        ServletRequest request = e.getSuppliedRequest();
        if (request.getAttribute("error") == null) {
          request.setAttribute("error", e.getThrowable());
        }
      }

      /** If another async is created (ex via asyncContext.dispatch), this needs to be re-attached */
      @Override public void onStartAsync(AsyncEvent e) {
        AsyncContext eventAsyncContext = e.getAsyncContext();
        if (eventAsyncContext != null) {
          eventAsyncContext.addListener(this, e.getSuppliedRequest(), e.getSuppliedResponse());
        }
      }

      @Override public String toString() {
        return "TracingAsyncListener{" + span + "}";
      }
    }

    static final class AsyncTimeoutException extends TimeoutException {
      AsyncTimeoutException(AsyncEvent e) {
        super("Timed out after " + e.getAsyncContext().getTimeout() + "ms");
      }

      @Override
      public Throwable fillInStackTrace() {
        return this; // stack trace doesn't add value as this is used in a callback
      }
    }
  }
}

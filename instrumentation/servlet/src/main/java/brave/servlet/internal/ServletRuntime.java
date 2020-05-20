/*
 * Copyright 2013-2020 The OpenZipkin Authors
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
package brave.servlet.internal;

import brave.Span;
import brave.http.HttpServerHandler;
import brave.http.HttpServerRequest;
import brave.http.HttpServerResponse;
import brave.servlet.TracingFilter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import static brave.internal.Throwables.propagateIfFatal;

/**
 * Access to servlet version-specific features
 *
 * <p>Originally designed by OkHttp team, derived from {@code okhttp3.internal.platform.Platform}
 */
public abstract class ServletRuntime {
  private static final ServletRuntime SERVLET_RUNTIME = findServletRuntime();

  public HttpServletResponse httpServletResponse(ServletResponse response) {
    return (HttpServletResponse) response;
  }

  /** public for {@link brave.servlet.HttpServletResponseWrapper}. */
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

  /** Attempt to match the host runtime to a capable Platform implementation. */
  private static ServletRuntime findServletRuntime() {
    // Find Servlet v3 new methods
    try {
      Class.forName("javax.servlet.AsyncEvent");
      HttpServletRequest.class.getMethod("isAsyncStarted");
      return new Servlet3(); // intentionally doesn't not access the type prior to the above guard
    } catch (NoSuchMethodException e) {
      // pre Servlet v3
    } catch (ClassNotFoundException e) {
      // pre Servlet v3
    }

    // compatible with Servlet 2.5
    return new Servlet25();
  }

  static final class Servlet3 extends ServletRuntime {
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
        Object sendHandled = req.getAttribute("brave.servlet.TracingFilter$SendHandled");
        if (sendHandled instanceof AtomicBoolean
            && ((AtomicBoolean) sendHandled).compareAndSet(false, true)) {
          HttpServletResponse res = (HttpServletResponse) e.getSuppliedResponse();

          HttpServerResponse response =
              brave.servlet.HttpServletResponseWrapper.create(req, res, e.getThrowable());
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

  static final class Servlet25 extends ServletRuntime {
    @Override public HttpServletResponse httpServletResponse(ServletResponse response) {
      return new Servlet25ServerResponseAdapter(response);
    }

    @Override public boolean isAsync(HttpServletRequest request) {
      return false;
    }

    @Override public void handleAsync(
      HttpServerHandler<HttpServerRequest, HttpServerResponse> handler,
      HttpServletRequest request, HttpServletResponse response, Span span) {
      assert false : "this should never be called in Servlet 2.5";
    }

    // copy-on-write global reflection cache outperforms thread local copies
    final AtomicReference<Map<Class<?>, Object>> classToGetStatus =
      new AtomicReference<>(new LinkedHashMap<>());
    static final String RETURN_NULL = "RETURN_NULL";

    /**
     * Even though the Servlet 2.5 version of HttpServletResponse doesn't have the getStatus method,
     * routine servlet runtimes, do, for example {@code org.eclipse.jetty.server.Response}
     */
    @Override public int status(HttpServletResponse response) {
      if (response instanceof Servlet25ServerResponseAdapter) {
        // servlet 2.5 doesn't have get status
        return ((Servlet25ServerResponseAdapter) response).getStatusInServlet25();
      }
      Class<? extends HttpServletResponse> clazz = response.getClass();
      Map<Class<?>, Object> classesToCheck = classToGetStatus.get();
      Object getStatusMethod = classesToCheck.get(clazz);
      if (getStatusMethod == RETURN_NULL ||
        (getStatusMethod == null && classesToCheck.size() == 10)) { // limit size
        return 0;
      }

      // Now, we either have a cached method or we have room to cache a method
      if (getStatusMethod == null) {
        if (clazz.isLocalClass() || clazz.isAnonymousClass()) return 0; // don't cache
        try {
          // we don't check for accessibility as isAccessible is deprecated: just fail later
          getStatusMethod = clazz.getMethod("getStatus");
          return (int) ((Method) getStatusMethod).invoke(response);
        } catch (Throwable throwable) {
          propagateIfFatal(throwable);
          getStatusMethod = RETURN_NULL;
          return 0;
        } finally {
          // regardless of success or fail, replace the cache
          Map<Class<?>, Object> replacement = new LinkedHashMap<>(classesToCheck);
          replacement.put(clazz, getStatusMethod);
          classToGetStatus.set(replacement); // lost race will reset, but only up to size - 1 times
        }
      }

      // if we are here, we have a cached method, that "should" never fail, but we check anyway
      try {
        return (int) ((Method) getStatusMethod).invoke(response);
      } catch (Throwable throwable) {
        propagateIfFatal(throwable);
        Map<Class<?>, Object> replacement = new LinkedHashMap<>(classesToCheck);
        replacement.put(clazz, RETURN_NULL);
        classToGetStatus.set(replacement); // prefer overriding on failure
        return 0;
      }
    }
  }

  /** When deployed in Servlet 2.5 environment {@link #getStatus} is not available. */
  static final class Servlet25ServerResponseAdapter extends HttpServletResponseWrapper {
    // The Servlet spec says: calling setStatus is optional, if no status is set, the default is OK.
    int httpStatus = HttpServletResponse.SC_OK;

    Servlet25ServerResponseAdapter(ServletResponse response) {
      super((HttpServletResponse) response);
    }

    // Do not use @Override annotation to avoid compatibility on deprecated methods
    public void setStatus(int sc, String sm) {
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

    int getStatusInServlet25() {
      return httpStatus;
    }
  }
}

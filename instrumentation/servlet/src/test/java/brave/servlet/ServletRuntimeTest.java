/*
 * Copyright 2013-2019 The OpenZipkin Authors
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
package brave.servlet;

import java.io.PrintWriter;
import java.util.Collection;
import java.util.Locale;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Response;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ServletRuntimeTest {
  ServletRuntime servlet25 = new ServletRuntime.Servlet25();

  @Test public void servlet25_status_doesntParseAnonymousTypes() throws Exception {
    // while looks nice, this will overflow our cache
    Response jettyResponse = new Response(null) {
      @Override public int getStatus() {
        throw new AssertionError();
      }
    };
    assertThat(servlet25.status(jettyResponse))
      .isZero();
  }

  @Test public void servlet25_status_doesntParseLocalTypes() throws Exception {
    // while looks nice, this will overflow our cache
    class LocalResponse extends HttpServletResponseImpl {
    }
    assertThat(servlet25.status(new LocalResponse()))
      .isZero();
  }

  class ExceptionResponse extends HttpServletResponseImpl {
    @Override public int getStatus() {
      throw new IllegalStateException("foo");
    }
  }

  @Test public void servlet25_status_nullOnException() throws Exception {
    assertThat(servlet25.status(new ExceptionResponse()))
      .isZero();
  }

  class Response1 extends HttpServletResponseImpl {
  }

  class Response2 extends HttpServletResponseImpl {
  }

  class Response3 extends HttpServletResponseImpl {
  }

  class Response4 extends HttpServletResponseImpl {
  }

  class Response5 extends HttpServletResponseImpl {
  }

  class Response6 extends HttpServletResponseImpl {
  }

  class Response7 extends HttpServletResponseImpl {
  }

  class Response8 extends HttpServletResponseImpl {
  }

  class Response9 extends HttpServletResponseImpl {
  }

  class Response10 extends HttpServletResponseImpl {
  }

  class Response11 extends HttpServletResponseImpl {
  }

  @Test public void servlet25_status_cachesUpToTenTypes() throws Exception {
    assertThat(servlet25.status(new Response1()))
      .isEqualTo(200);
    assertThat(servlet25.status(new Response2()))
      .isEqualTo(200);
    assertThat(servlet25.status(new Response3()))
      .isEqualTo(200);
    assertThat(servlet25.status(new Response4()))
      .isEqualTo(200);
    assertThat(servlet25.status(new Response5()))
      .isEqualTo(200);
    assertThat(servlet25.status(new Response6()))
      .isEqualTo(200);
    assertThat(servlet25.status(new Response7()))
      .isEqualTo(200);
    assertThat(servlet25.status(new Response8()))
      .isEqualTo(200);
    assertThat(servlet25.status(new Response9()))
      .isEqualTo(200);
    assertThat(servlet25.status(new Response10()))
      .isEqualTo(200);
    assertThat(servlet25.status(new Response11()))
      .isZero();
  }

  public static class HttpServletResponseImpl implements HttpServletResponse {
    @Override public String getCharacterEncoding() {
      return null;
    }

    @Override public String getContentType() {
      return null;
    }

    @Override public ServletOutputStream getOutputStream() {
      return null;
    }

    @Override public PrintWriter getWriter() {
      return null;
    }

    @Override public void setCharacterEncoding(String charset) {
    }

    @Override public void setContentLength(int len) {
    }

    @Override public void setContentType(String type) {
    }

    @Override public void setBufferSize(int size) {
    }

    @Override public int getBufferSize() {
      return 0;
    }

    @Override public void flushBuffer() {
    }

    @Override public void resetBuffer() {
    }

    @Override public boolean isCommitted() {
      return false;
    }

    @Override public void reset() {
    }

    @Override public void setLocale(Locale loc) {
    }

    @Override public Locale getLocale() {
      return null;
    }

    @Override public void addCookie(Cookie cookie) {
    }

    @Override public boolean containsHeader(String name) {
      return false;
    }

    @Override public String encodeURL(String url) {
      return null;
    }

    @Override public String encodeRedirectURL(String url) {
      return null;
    }

    @Override public String encodeUrl(String url) {
      return null;
    }

    @Override public String encodeRedirectUrl(String url) {
      return null;
    }

    @Override public void sendError(int sc, String msg) {
    }

    @Override public void sendError(int sc) {
    }

    @Override public void sendRedirect(String location) {
    }

    @Override public void setDateHeader(String name, long date) {
    }

    @Override public void addDateHeader(String name, long date) {
    }

    @Override public void setHeader(String name, String value) {
    }

    @Override public void addHeader(String name, String value) {
    }

    @Override public void setIntHeader(String name, int value) {
    }

    @Override public void addIntHeader(String name, int value) {
    }

    @Override public void setStatus(int sc) {
    }

    @Override public void setStatus(int sc, String sm) {
    }

    @Override public int getStatus() {
      return 200;
    }

    @Override public String getHeader(String name) {
      return null;
    }

    @Override public Collection<String> getHeaders(String name) {
      return null;
    }

    @Override public Collection<String> getHeaderNames() {
      return null;
    }
  }
}

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
import java.util.concurrent.TimeUnit;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class ServletRuntimeBenchmarks {
  static final ServletRuntime servlet3 = new ServletRuntime.Servlet3();
  static final ServletRuntime servlet25 = new ServletRuntime.Servlet25();

  @Benchmark @Group("no_contention") @GroupThreads(1)
  public int no_contention_status_servlet3() {
    return threeStatuses(servlet3);
  }

  @Benchmark @Group("mild_contention") @GroupThreads(2)
  public int mild_contention_status_servlet3() {
    return threeStatuses(servlet3);
  }

  @Benchmark @Group("high_contention") @GroupThreads(8)
  public int high_contention_status_servlet3() {
    return threeStatuses(servlet3);
  }

  int threeStatuses(ServletRuntime runtime) {
    // & to ensure null wasn't returned (forces NPE)
    return runtime.status(new Response1()) &
      runtime.status(new Response2()) &
      runtime.status(new Response3());
  }

  @Benchmark @Group("no_contention") @GroupThreads(1)
  public int no_contention_httpResponse_servlet3() {
    return threeResponses(servlet3);
  }

  @Benchmark @Group("mild_contention") @GroupThreads(2)
  public int mild_contention_httpResponse_servlet3() {
    return threeResponses(servlet3);
  }

  @Benchmark @Group("high_contention") @GroupThreads(8)
  public int high_contention_httpResponse_servlet3() {
    return threeResponses(servlet3);
  }

  int threeResponses(ServletRuntime runtime) {
    // & to ensure null wasn't returned (forces NPE)
    return runtime.httpResponse(new Response1()).getStatus() &
      runtime.httpResponse(new Response2()).getStatus() &
      runtime.httpResponse(new Response3()).getStatus();
  }

  @Benchmark @Group("no_contention") @GroupThreads(1)
  public int no_contention_status_servlet25() {
    return threeStatuses(servlet25);
  }

  @Benchmark @Group("mild_contention") @GroupThreads(2)
  public int mild_contention_status_servlet25() {
    return threeStatuses(servlet25);
  }

  @Benchmark @Group("high_contention") @GroupThreads(8)
  public int high_contention_status_servlet25() {
    return threeStatuses(servlet25);
  }

  @Benchmark @Group("no_contention") @GroupThreads(1)
  public int no_contention_httpResponse_servlet25() {
    return threeResponses(servlet25);
  }

  @Benchmark @Group("mild_contention") @GroupThreads(2)
  public int mild_contention_httpResponse_servlet25() {
    return threeResponses(servlet25);
  }

  @Benchmark @Group("high_contention") @GroupThreads(8)
  public int high_contention_httpResponse_servlet25() {
    return threeResponses(servlet25);
  }

  @Benchmark @Group("no_contention") @GroupThreads(1)
  public int no_contention_status_reflection() throws Exception {
    return threeStatusesReflection();
  }

  @Benchmark @Group("mild_contention") @GroupThreads(2)
  public int mild_contention_status_reflection() throws Exception {
    return threeStatusesReflection();
  }

  @Benchmark @Group("high_contention") @GroupThreads(8)
  public int high_contention_status_reflection() throws Exception {
    return threeStatusesReflection();
  }

  private int threeStatusesReflection() throws Exception {
    // & to ensure null wasn't returned (forces NPE)
    return ((int) Response1.class.getMethod("getStatus").invoke(new Response1())) &
      ((int) Response2.class.getMethod("getStatus").invoke(new Response2())) &
      ((int) Response3.class.getMethod("getStatus").invoke(new Response3()));
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .include(".*" + ServletRuntimeBenchmarks.class.getSimpleName() + ".*")
      .build();

    new Runner(opt).run();
  }

  static class Response1 extends HttpServletResponseImpl {
  }

  static class Response2 extends HttpServletResponseImpl {
  }

  static class Response3 extends HttpServletResponseImpl {
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

    @Override public void setContentLengthLong(long len) {

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

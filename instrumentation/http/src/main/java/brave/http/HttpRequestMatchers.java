/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.http;

import brave.sampler.Matcher;
import brave.sampler.Matchers;

/**
 * Null safe matchers for use in {@link HttpRuleSampler}.
 *
 * @see Matchers
 * @since 5.8
 */
public final class HttpRequestMatchers {

  /** Matcher for case-sensitive HTTP methods, such as "GET" and "POST" */
  public static Matcher<HttpRequest> methodEquals(String method) {
    if (method == null) throw new NullPointerException("method == null");
    if (method.isEmpty()) throw new NullPointerException("method is empty");
    return new MethodEquals(method);
  }

  static final class MethodEquals implements Matcher<HttpRequest> {
    final String method;

    MethodEquals(String method) {
      this.method = method;
    }

    @Override public boolean matches(HttpRequest request) {
      return method.equals(request.method());
    }

    @Override public boolean equals(Object o) {
      if (o == this) return true;
      if (!(o instanceof MethodEquals)) return false;
      MethodEquals that = (MethodEquals) o;
      return method.equals(that.method);
    }

    @Override public int hashCode() {
      return method.hashCode();
    }

    @Override public String toString() {
      return "MethodEquals(" + method + ")";
    }
  }

  public static Matcher<HttpRequest> pathStartsWith(String pathPrefix) {
    if (pathPrefix == null) throw new NullPointerException("pathPrefix == null");
    if (pathPrefix.isEmpty()) throw new NullPointerException("pathPrefix is empty");
    return new PathStartsWith(pathPrefix);
  }

  static final class PathStartsWith implements Matcher<HttpRequest> {
    final String pathPrefix;

    PathStartsWith(String pathPrefix) {
      this.pathPrefix = pathPrefix;
    }

    @Override public boolean matches(HttpRequest request) {
      String requestPath = request.path();
      return requestPath != null && requestPath.startsWith(pathPrefix);
    }

    @Override public boolean equals(Object o) {
      if (o == this) return true;
      if (!(o instanceof PathStartsWith)) return false;
      PathStartsWith that = (PathStartsWith) o;
      return pathPrefix.equals(that.pathPrefix);
    }

    @Override public int hashCode() {
      return pathPrefix.hashCode();
    }

    @Override public String toString() {
      return "PathStartsWith(" + pathPrefix + ")";
    }
  }
}

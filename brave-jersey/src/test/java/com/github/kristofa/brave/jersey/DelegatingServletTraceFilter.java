package com.github.kristofa.brave.jersey;

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/** Jersey filters are instantiated by class, requiring us to play games. */
final class DelegatingServletTraceFilter implements Filter {
  static volatile Filter delegate;
  static volatile FilterConfig filterConfig;

  static void setFilter(Filter filter) throws ServletException {
    delegate = filter;
    if (delegate != null && filterConfig != null) delegate.init(filterConfig);
  }

  @Override public void init(FilterConfig filterConfig) throws ServletException {
    if (delegate != null) delegate.init(filterConfig);
    this.filterConfig = filterConfig;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (delegate == null) {
      chain.doFilter(request, response);
      return;
    }
    delegate.doFilter(request, response, chain);
  }

  @Override public void destroy() {
    if (delegate != null) delegate.destroy();
  }
}

# brave-instrumentation-servlet
This module contains a tracing filter for Servlet 2.5+ (including Async).
`TracingFilter` extracts trace state from incoming requests. Then, it
reports Zipkin how long each request takes, along with relevant tags
like the http url.

To enable tracing, you need to add the `TracingFilter`.

Here's a Servlet 3+ example:
```java
public class TracingServletContextListener implements ServletContextListener {

  @Override
  public void contextInitialized(ServletContextEvent servletContextEvent) {
    Tracing tracing = Tracing.newBuilder().localServiceName("myservicename").build();
    servletContextEvent
        .getServletContext()
        .addFilter("TracingFilter", TracingFilter.create(tracing))
        .addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
  }

  @Override
  public void contextDestroyed(ServletContextEvent servletContextEvent) {
  }
}
```

In Servlet 2.5, there's no `ServletContextListener`. Besides using tools
like Spring or Guice, you can make a decorating `javax.servlet.Filter`
that configures tracing, and add that to your web.xml.
```java
public class ServletContextTracingFilter implements Filter {

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    Filter currentTracingFilter =
        (Filter) request.getServletContext().getAttribute(TracingFilter.class.getName());
    if (currentTracingFilter == null) {
      chain.doFilter(request, response);
    } else {
      currentTracingFilter.doFilter(request, response, chain);
    }
  }

  @Override
  public void init(FilterConfig filterConfig) {
    Tracing tracing = Tracing.newBuilder().localServiceName("myservicename").build();
    Filter tracingFilter = TracingFilter.create(tracing);
    filterConfig.getServletContext().setAttribute(TracingFilter.class.getName(), tracingFilter);
  }

  @Override public void destroy() {
  }
}
```

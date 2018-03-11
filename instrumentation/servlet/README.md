# brave-instrumentation-servlet
This module contains a tracing filter for Servlet 2.5+ (including Async).
`TracingFilter` extracts trace state from incoming requests. Then, it
reports Zipkin how long each request takes, along with relevant tags
like the http url.

## Configuration
To enable tracing, you need to add the `TracingFilter`. If using Spring,
we provide a [built-in delegate](../spring-webmvc) to configure it.
Otherwise, use a different framework or look at one of the manually
configured examples below.

### Servlet 3+
When configuring, make sure this is set for all paths, all dispatcher
types and matches after other filters. Otherwise, you may miss spans or
leak unfinished asynchronous spans.

Here's an example Servlet 3+ listener
```java
public class TracingServletContextListener implements ServletContextListener {
  Sender sender = OkHttpSender.create("http://127.0.0.1:9411/api/v2/spans");
  AsyncReporter<Span> spanReporter = AsyncReporter.create(sender);
  Tracing tracing = Tracing.newBuilder()
        .localServiceName("my-service-name")
        .spanReporter(spanReporter).build();

  @Override
  public void contextInitialized(ServletContextEvent servletContextEvent) {
    servletContextEvent
        .getServletContext()
        .addFilter("tracingFilter", TracingFilter.create(tracing))
        .addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
  }

  @Override
  public void contextDestroyed(ServletContextEvent servletContextEvent) {
    try {
      tracing.close(); // disables Tracing.current()
      spanReporter.close(); // stops reporting thread and flushes data
      sender.close(); // closes any transport resources
    } catch (IOException e) {
      // do something real
    }
  }
}
```

### Servlet 2.5

In Servlet 2.5, there's no `ServletContextListener`. Besides using tools
like Spring or Guice, you can make a delegating `javax.servlet.Filter`
that configures tracing, and add that to your web.xml.
```xml
  <filter>
    <filter-name>tracingFilter</filter-name>
    <filter-class>com.myco.DelegatingTracingFilter</filter-class>
  </filter>

  <filter-mapping>
    <filter-name>tracingFilter</filter-name>
    <url-pattern>/*</url-pattern>
  </filter-mapping>
```

Here's an example implementation:
```java
public class DelegatingTracingFilter implements Filter {
  Sender sender = OkHttpSender.create("http://127.0.0.1:9411/api/v2/spans");
  AsyncReporter<Span> spanReporter = AsyncReporter.create(sender);
  Tracing tracing = Tracing.newBuilder()
        .localServiceName("my-service-name")
        .spanReporter(spanReporter).build();
  Filter delegate = TracingFilter.create(tracing);

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    delegate.doFilter(request, response, chain);
  }

  @Override public void destroy() {
    try {
      tracing.close(); // disables Tracing.current()
      spanReporter.close(); // stops reporting thread and flushes data
      sender.close(); // closes any transport resources
    } catch (IOException e) {
      // do something real
    }
  }
}
```

## Collaborating with `TracingFilter`

`TracingFilter` sets the servlet attributes so that you can access span
data without relying on implicit context:
* `brave.SpanCustomizer` - add tags or rename the span without a tracer
* `brave.propagation.TraceContext` - shows trace IDs and any extra data

Ex: The following
```java
SpanCustomizer customizer = (SpanCustomizer) request.getAttribute(SpanCustomizer.class.getName());
if (customizer != null) customizer.tag("platform", "XX");
```

`TracingFilter` also looks for the attribute "http.route". When present,
this is available to normal parsing, for example a route-base span name.
This feature allows frameworks like spring-webmvc to contribute
controller information with less code duplication.

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

`TracingFilter` adds some utilities as request attributes, so that you
can read the trace context or write span data with less coupling. These
attributes have the same name as their corresponding Java types:
* `brave.SpanCustomizer` - add tags or rename the span without a tracer
* `brave.propagation.TraceContext` - shows trace IDs and any extra data

Ex: The following
```java
SpanCustomizer customizer = (SpanCustomizer) request.getAttribute(SpanCustomizer.class.getName());
if (customizer != null) customizer.tag("platform", "XX");
```

`TracingFilter` looks for request attributes when completing a span.
When integrating higher level frameworks, set the following attributes:

* "http.route" - A string representing the route which this request matched or
                 "" (empty string), if there was no match. Ex "/users/{userId}"
                 Only set "http.route" when routing is supported!
* "error" - An instance of `Throwable` raised when processing a user handler.
            This is only necessary when the error is not propagated to the
            Servlet layer.

Ex. To copy the Spring match pattern as the "http.route":
```java
Object httpRoute = request.getAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE);
request.setAttribute("http.route", httpRoute != null ? httpRoute.toString() : "");
```

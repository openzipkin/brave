# brave-instrumentation-spring-webmvc
This module contains a tracing filter and span customizing interceptors for [Spring WebMVC](https://docs.spring.io/spring/docs/current/spring-framework-reference/html/mvc.html)

`DelegatingTracingFilter` initializes [our tracing filter](../servlet)
from the application context. `SpanCustomizingAsyncHandlerInterceptor` and
`SpanCustomizingHandlerInterceptor` layer over that adding controller tags
and route information to servlet-originated spans.

## Configuration
If using Spring Boot, [spring-cloud-sleuth](https://github.com/spring-cloud/spring-cloud-sleuth) fully
configures tracing components in this module automatically. Servlet
based applications require configuration of `HttpTracing`,
`DelegatingTracingFilter` and `SpanCustomizingAsyncHandlerInterceptor`.

Full example setup are available in [XML](https://github.com/openzipkin/brave-webmvc-example/blob/master/webmvc25/src/main/webapp/WEB-INF/applicationContext.xml) and [Java](https://github.com/openzipkin/brave-webmvc-example/blob/master/webmvc4/src/main/java/brave/webmvc/TracingConfiguration.java).

### Generic Tracing Configuration (`HttpTracing`)
Tracing always needs a bean of type `HttpTracing` in application scope.
Make sure it is in place before proceeding.

`ContextLoaderListener` goes in web.xml, making the application context
visible to filters.
```xml
<listener>
  <listener-class>org.springframework.web.context.ContextLoaderListener</listener-class>
</listener>
```

`getRootConfigClasses` does the same for Servlet 3.0 initializers:
```java
public class Initializer extends AbstractAnnotationConfigDispatcherServletInitializer {
  @Override protected Class<?>[] getRootConfigClasses() {
    return new Class[] {TracingConfiguration.class, AppConfiguration.class};
  }
--snip--
```

### Tracing Filter Setup (`DelegatingTracingFilter`)
`DelegatingTracingFilter` sets the base layer of tracing, for example
starting spans per-request. All you have to do is install it in web.xml
or an initializer:

Here's the change to web.xml:
```xml
<filter>
  <filter-name>tracingFilter</filter-name>
  <filter-class>brave.spring.webmvc.DelegatingTracingFilter</filter-class>
</filter>
<filter-mapping>
  <filter-name>tracingFilter</filter-name>
  <url-pattern>/*</url-pattern>
</filter-mapping>
```

or the same for Servlet 3.0 initializers:
```java
public class Initializer extends AbstractAnnotationConfigDispatcherServletInitializer {
  /** Ensures tracing is setup for all HTTP requests. */
  @Override protected Filter[] getServletFilters() {
    return new Filter[] {new DelegatingTracingFilter()};
  }
--snip--
```

### WebMVC-specific data (`SpanCustomizingAsyncHandlerInterceptor`)
`SpanCustomizingAsyncHandlerInterceptor` (and the non async counterpart
for Spring WebMVC 2.5) add controller information to your spans.

Here's an example of using the synchronous handler, which works with Spring 2.5+
```xml
<mvc:interceptors>
  <bean class="brave.spring.servlet.SpanCustomizingHandlerInterceptor"/>
</mvc:interceptors>
```

Here's an example of the asynchronous-capable handler, which works with Spring 3+
```java
@Configuration
@EnableWebMvc
class TracingConfig extends WebMvcConfigurerAdapter {
  @Autowired
  private SpanCustomizingAsyncHandlerInterceptor tracingInterceptor;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(tracingInterceptor);
  }
}
```

## Customizing Span data based on controllers
`HandlerParser` decides which controller-specific (data beyond normal
http tags) end up on the span. You can override this to change what's
parsed, or use `NOOP` to disable controller-specific data.

Ex. If you want less tags, you can disable the WebMVC controller ones.
```java
@Bean handlerParser() {
  return HandlerParser.NOOP;
}
```

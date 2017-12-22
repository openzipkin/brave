# brave-instrumentation-spring-webmvc
This module contains tracing interceptors for [Spring WebMVC](https://docs.spring.io/spring/docs/current/spring-framework-reference/html/mvc.html)
`TracingAsyncHandlerInterceptor` and `TracingHandlerInterceptor` extract trace state from incoming
requests. Then, they report Zipkin how long each request takes, along with relevant tags like the
http url.

## Configuration

Tracing always needs a bean of type `HttpTracing` configured. Make sure
it is in place before proceeding. Here's an example in [XML](https://github.com/openzipkin/brave-webmvc-example/blob/master/webmvc25/src/main/webapp/WEB-INF/spring-webmvc-servlet.xml) and [Java](https://github.com/openzipkin/brave-webmvc-example/blob/master/webmvc4/src/main/java/brave/webmvc/TracingConfiguration.java).

Then, configure `TracingHandlerInterceptor` in either XML or Java.

Here's an example of using the synchronous handler, which works with Spring 2.5+
```xml
<mvc:interceptors>
  <bean class="brave.spring.servlet.TracingHandlerInterceptor"/>
</mvc:interceptors>
```

Here's an example of the asynchronous-capable handler, which works with Spring 3+
```java
@Configuration
@EnableWebMvc
class TracingConfig extends WebMvcConfigurerAdapter {
  @Bean AsyncHandlerInterceptor tracingInterceptor(HttpTracing httpTracing) {
    return TracingAsyncHandlerInterceptor.create(httpTracing);
  }

  @Autowired
  private AsyncHandlerInterceptor tracingInterceptor;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(tracingInterceptor);
  }
}
```

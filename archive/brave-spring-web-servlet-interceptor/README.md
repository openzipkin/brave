# brave-spring-web-servlet-interceptor

Interceptor for servlet integration, which will create or resume a trace
as required.

## Configuration

Tracing always needs beans of type `Brave` and `SpanNameProvider`
configured. Make sure these are in place before proceeding.

Then, configure `ServletHandlerInterceptor` in either XML or Java.

```xml
<mvc:interceptors>
  <bean class="com.github.kristofa.brave.spring.ServletHandlerInterceptor">
  </bean>
</mvc:interceptors>
```

```java
@Configuration
@Import(ServletHandlerInterceptor.class)
public class WebConfig extends WebMvcConfigurerAdapter {

    @Autowired
    private ServletHandlerInterceptor interceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor);
    }

}
```
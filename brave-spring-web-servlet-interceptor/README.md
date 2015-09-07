
The `ServletHandlerInterceptor` can be used to handle the server side integration, which will set up the
trace information or create as required. This can be configured in either XML or Java.

```xml
<mvc:interceptors>
  <bean class="com.github.kristofa.brave.spring.ServletHandlerInterceptor">
    <constructor-arg name="spanNameProvider">
      <bean class="com.github.kristofa.brave.http.DefaultSpanNameProvider"/>
    </constructor-arg>
  </bean>
</mvc:interceptors>
```

```java
public class WebConfig extends WebMvcConfigurerAdapter {

    @Autowired
    private ServerRequestInterceptor requestInterceptor;

    @Autowired
    private ServerResponseInterceptor responseInterceptor;

    @Autowired
    private ServerSpanThreadBinder serverThreadBinder;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ServletHandlerInterceptor(requestInterceptor, responseInterceptor, new DefaultSpanNameProvider(), serverThreadBinder));
    }

}
```
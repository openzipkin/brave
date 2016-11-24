
The `ServletHandlerInterceptor` can be used to handle the server side integration, which will set up the
trace information or create as required. This can be configured in either XML or Java.

```xml
<mvc:interceptors>
  <bean class="com.github.kristofa.brave.spring.ServletHandlerInterceptor">
  </bean>
</mvc:interceptors>
```

```java
public class WebConfig extends WebMvcConfigurerAdapter {

    @Autowired
    private Brave brave;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(ServletHandlerInterceptor.create(brave));
    }

}
```
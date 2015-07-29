# brave-impl-spring #

Latest release available in Maven central:

    <dependency>
        <groupId>com.github.kristofa</groupId>
        <artifactId>brave-impl-spring</artifactId>
        <version>2.4.1</version>
    </dependency>


The brave-impl-spring module has Spring dependency injection configuration classes for the
brave-impl api objects (Java based container configuration). 

It does not use XML configuration but Java based container configuration using annotations.

If you include this module on your classpath you can add the configurations to your Spring
context by including com.github.kristofa.brave to the classpath scanning path or by adding 
the individual config classes.

Spring is added as a Maven dependency with 'provided' scope so you have to include Spring as compile scope
dependency to you own application. This gives you the freedom to choose the Spring version of 
your choice (the config classes are tested with Spring 3.2.2).

There are no configuration classes provided for SpanCollector and TraceFilters because you
probably want the freedom to choose these for yourself based on your application. There are
however Configuration classes that need those (SpanCollector, TraceFilters) as a dependency!

Configuration classes are available for:

*   AnnotationSubmitter
*   ClientTracer: A ClientTracer needs a SpanCollector and TraceFilters so you have to make
sure you have these added to Spring context yourself otherwise instantiating of ClientTracer will fail.
*   EndpointSubmitter
*   ServerSpanThreadBinder
*   ServerTracer: A ServerTracer needs a SpanCollector so you have to make sure a configuration for
SpanCollector is added to your Spring context.

There is also a TraceFilters class which is a wrapper around a List of TraceFilter instances.
Reason is that injecting generic types is not possible to instead the ClientTracerConfig class
relies on a TraceFilters instance to be available on the Spring Context.


The `ServletHandlerInterceptor` can be used to handle the server side integration, which will set up the
trace information or create as required. This can be configured in either XML or Java.

```xml
<mvc:interceptors>
    <bean class="com.github.kristofa.brave.ServletHandlerInterceptor" />
</mvc:interceptors>
```

```java
public class WebConfig extends WebMvcConfigurerAdapter {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ServletHandlerInterceptor());
    }

}
```

If you use `RestTemplate` to communicate between your services, you can add the `BraveClientHttpRequestInterceptor`
to automatically add the headers to outgoing requests. To add the interceptor, use the the `RestTemplate.setInterceptors` method.

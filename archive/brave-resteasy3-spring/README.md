# brave-resteasy3-spring #

The brave-resteasy3-spring module provides Resteasy 3.x client and server support which will allow Brave to be used with any
existing Resteasy 3.x application with minimal configuration. As both Jersey 2 and Resteasy 3 are both based on JAX-RS 2 
common part is in `brave-jaxrs2` module

## Configuration

Tracing always needs beans of type `Brave` and `SpanNameProvider`
configured. Make sure these are in place before proceeding.

To setup Resteasy 3.x tracing, tell Spring to configure
`com.github.kristofa.brave.resteasy3.BraveTracingFeatureConfiguration`.

There's nothing further needed for server tracing. To setup client
tracing, register the request and response filters like below:

```java
ResteasyClient client = new ResteasyClientBuilder()
    .register(appContext.getBean(BraveTracingFeature.class))
    .build();
```

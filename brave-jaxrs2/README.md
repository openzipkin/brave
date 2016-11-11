# brave-jaxrs2 #

The brave-jaxrs2 module provides JAX-RS 2.x compatible client and server support which will allow Brave to be used with any
existing JAX-RS 2.x application with minimal configuration.

The module contains 4 filters:

*   `BraveContainerRequestFilter`  - Intercepts incoming container requests and extracts any trace information from
the request header. Also sends sr annotations.
*   `BraveContainerResponseFilter` - Intercepts outgoing container responses and sends ss annotations.
*   `BraveClientRequestFilter` - Intercepts JAX-RS 2.x client requests and adds or forwards tracing information in the header.
Also sends cs annotations.
*   `BraveClientResponseFilter` - Intercepts JAX-RS 2.x client responses and sends cr annotations. Also submits the completed span.

For convenience there is also a JAX-RS Feature `BraveTracingFeature` that can be added to clients as well as services.

## Configuration

### Dependency Injection
If using Resteasy 3 with Spring, see [../brave-resteasy3-spring/README.md]. 
If using Jersey 2 with Spring, see [../brave-jersey2/README.md]. 

Tracing always needs instances of type `Brave` and `SpanNameProvider`
configured. Make sure these are in place before proceeding.

Then, use your dependency injection library to configure `BraveTracingFeature`.
this will setup filters automatically.

### Manual

For server side setup, an Application class could look like this:

```java
public class MyApplication extends Application {

  public Set<Object> getSingletons() {
    Brave brave = new Brave.Builder().build();
    BraveTracingFeature tracingFeature = BraveTracingFeature.create(brave);
    return new LinkedHashSet<>(Arrays.asList(tracingFeature));
  }

}
```
	
For CXF it looks like:

```java
JAXRSServerFactoryBean factory = new JAXRSServerFactoryBean();
...
factory.setProviders(Arrays.asList(BraveTracingFeature.create(brave)));
factory.create();
```

For client side setup, you just have to register the BraveTracingFeature
with your JAX-RS 2 client before you make your request:

```java
client = ClientBuilder.newBuilder()
    .register(BraveTracingFeature.create(brave))
    .build();
```

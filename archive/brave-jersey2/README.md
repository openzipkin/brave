# brave-jersey2

The brave-jersey2 module provides Jersey 2.x client and server support
which will allow Brave to be used with any existing Jersey 2.x application
with minimal configuration. As both Jersey 2 and Resteasy 3 are both based on JAX-RS 2 
common part is in `brave-jaxrs2` module

## Configuration

### Dependency Injection
Tracing always needs instances of type `Brave` and `SpanNameProvider`
configured. Make sure these are in place before proceeding.

Next, configure `com.github.kristofa.brave.jaxrs2.BraveTracingFeature`,
this will setup container filters automatically.

In your web.xml:

```xml
<init-param>
    <param-name>jersey.config.server.provider.packages</param-name>
    <param-value>
        my.existing.packages,com.github.kristofa.brave.jaxrs2
    </param-value>
</init-param>
```

### Manual

Alternatively, you can use ResourceConfig to setup the `BraveTracingFeature`:

```java
resourceConfig.register(BraveTracingFeature.create(brave))
```

For client side setup, you just have to register `BraveTracingFeature`
with your Jersey client before you make your request.

It should look something like:

```java
WebTarget target = target("/mytarget");
target.register(BraveTracingFeature.create(brave));
```

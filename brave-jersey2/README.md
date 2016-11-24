# brave-jersey2 #

The brave-jersey2 module provides Jersey 2.x client and server support which will allow Brave to be used with any
existing Jersey 2.x application with minimal configuration. As both Jersey 2 and Resteasy 3 are both based on JAX-RS 2 
common part is in `brave-jaxrs2` module

## Usage ##

For server side setup, you simply need to tell Jersey to scan the `com.github.kristofa.brave.jaxrs2` package, and
the container filters will be picked up and registered automatically.

In your web.xml:

```xml
<init-param>
    <param-name>jersey.config.server.provider.packages</param-name>
    <param-value>
        my.existing.packages,com.github.kristofa.brave.jaxrs2
    </param-value>
</init-param>
```

For client side setup, you just have to register the client filters with your Jersey client before you make your request.

It should look something like:

```java
WebTarget target = target("/mytarget");
target.register(BraveClientRequestFilter.create(brave));
target.register(BraveClientResponseFilter.create(brave));
```

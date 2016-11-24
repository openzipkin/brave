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

## Usage ##

For server side setup, you simply need to tell JAX-RS 2.x to scan the com.github.kristofa.brave.jaxrs2 package, and
the container filters will be picked up and registered automatically.

for Resteasy 3 and Jersey 2 examples check corresponding modules documentation

For client side setup, you just have to register the client filters with your JAX-RS 2 client before you make your request.

It should look something like:

```java
Client client = ClientBuilder.newClient();
client.register(BraveClientRequestFilter.create(brave));
client.register(BraveClientResponseFilter.create(brave));
```

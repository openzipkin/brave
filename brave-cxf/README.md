# brave-cxf #

The brave-cxf module provides CXF 3.x.x compatible client and server support which will allow Brave to be used with any
existing CXF 3.x.x application with minimal configuration.

Supports both JAX-WS and JAX-RS.

The module contains 4 filters:

*   `BraveServerInInterceptor`  - Intercepts incoming server requests and extracts any trace information from
the request header. Also sends sr annotations.
*   `BraveServerOutInterceptor` - Intercepts outgoing server responses and sends ss annotations.
*   `BraveClientOutInterceptor` - Intercepts CXF 3.x.x client requests and adds or forwards tracing information in the header.
Also sends cs annotations.
*   `BraveClientOutInterceptor` - Intercepts CXF 3.x.x client responses and sends cr annotations. Also submits the completed span.

## Usage ##

For server side setup, you simply need to tell CXF 3.x.x to scan the com.github.kristofa.brave.jaxrs2 package, and
the container filters will be picked up and registered automatically.

CXF JAX-WS server setup:

```java
// setup or autowire brave
Brave brave = ...

// setup or autowire SpanNameProvider
SpanNameProvider spanNameProvider = ...

// setup server interceptors
BraveServerInInterceptor serverInInterceptor = new BraveServerInInterceptor(brave.serverRequestInterceptor(), spanNameProvider);
BraveServerOutInterceptor serverOutInterceptor = new BraveServerOutInterceptor(brave.serverResponseInterceptor());

// setup jax-ws server
JaxWsServerFactoryBean serverFactory = new JaxWsServerFactoryBean();
serverFactory.setAddress("http://localhost:9000/test");
serverFactory.setServiceClass(FooService.class);
serverFactory.setServiceBean(fooServiceImplementation);
serverFactory.getInInterceptors().add(serverInInterceptor);
serverFactory.getOutInterceptors().add(serverOutInterceptor);

serverFactory.create();
```

CXF JAX-WS client setup:

```java
// setup or autowire brave
Brave brave = ...

// setup or autowire SpanNameProvider
SpanNameProvider spanNameProvider = ...

BraveClientInInterceptor clientInInterceptor = new BraveClientInInterceptor(brave.clientResponseInterceptor());
BraveClientOutInterceptor clientOutInterceptor = new BraveClientOutInterceptor(spanNameProvider, brave.clientRequestInterceptor());

// setup jax-ws client
JaxWsProxyFactoryBean clientFactory = new JaxWsProxyFactoryBean();
clientFactory.setAddress("http://localhost:9000/test");
clientFactory.setServiceClass(FooService.class);
clientFactory.getInInterceptors().add(clientInInterceptor);
clientFactory.getOutInterceptors().add(clientOutInterceptor);
FooService client = (FooService) clientFactory.create();
```

CXF JAX-RS server setup:

```java
// setup or autowire brave
Brave brave = ...

// setup or autowire SpanNameProvider
SpanNameProvider spanNameProvider = ...

// setup server interceptors
BraveServerInInterceptor serverInInterceptor = new BraveServerInInterceptor(brave.serverRequestInterceptor(), spanNameProvider);
BraveServerOutInterceptor serverOutInterceptor = new BraveServerOutInterceptor(brave.serverResponseInterceptor());

// setup jax-ws server
JAXRSServerFactoryBean serverFactory = new JAXRSServerFactoryBean();
serverFactory.setServiceBeans(new RestFooService());
serverFactory.setAddress("http://localhost:9001/");
clientFactory.getInInterceptors().add(clientInInterceptor);
clientFactory.getOutInterceptors().add(clientOutInterceptor);
serverFactory.create();
```

CXF JAX-RS client setup:

```java
// setup or autowire brave
Brave brave = ...

// setup or autowire SpanNameProvider
SpanNameProvider spanNameProvider = ...

BraveClientInInterceptor clientInInterceptor = new BraveClientInInterceptor(brave.clientResponseInterceptor());
BraveClientOutInterceptor clientOutInterceptor = new BraveClientOutInterceptor(spanNameProvider, brave.clientRequestInterceptor());

// setup jax-ws client

JAXRSClientFactoryBean clientFactory = new JAXRSClientFactoryBean();
clientFactory.setAddress("http://localhost:9001/");
clientFactory.setServiceClass(FooService.class);
clientFactory.getInInterceptors().add(clientInInterceptor);
clientFactory.getOutInterceptors().add(clientOutInterceptor);
FooService client = (FooService) clientFactory.create();
```

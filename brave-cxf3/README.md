# brave-cxf3

The brave-cxf3 module provides CXF 3.x compatible client and server support which will allow Brave to be used with any
existing CXF 3.x application with minimal configuration.

Supports both JAX-WS and JAX-RS.

The module contains 4 filters:

*   `BraveServerInInterceptor`  - Intercepts incoming server requests and extracts any trace information from
the request header. Also sends sr annotations.
*   `BraveServerOutInterceptor` - Intercepts outgoing server responses and sends ss annotations.
*   `BraveClientOutInterceptor` - Intercepts CXF 3.x client requests and adds or forwards tracing information in the header.
Also sends cs annotations.
*   `BraveClientInInterceptor` - Intercepts CXF 3.x client responses and sends cr annotations. Also submits the completed span.

## Configuration

### JAX-WS

CXF JAX-WS server setup:

```java
JaxWsServerFactoryBean serverFactory = new JaxWsServerFactoryBean();
serverFactory.setAddress("http://localhost:9000/test");
serverFactory.setServiceClass(FooService.class);
serverFactory.setServiceBean(fooServiceImplementation);
serverFactory.getInInterceptors().add(BraveServerInInterceptor.create(brave));
serverFactory.getOutInterceptors().add(BraveServerOutInterceptor.create(brave));

serverFactory.create();
```

CXF JAX-WS client setup:

```java
JaxWsProxyFactoryBean clientFactory = new JaxWsProxyFactoryBean();
clientFactory.setAddress("http://localhost:9000/test");
clientFactory.setServiceClass(FooService.class);
clientFactory.getInInterceptors().add(BraveClientInInterceptor.create(brave));
clientFactory.getOutInterceptors().add(BraveClientOutInterceptor.create(brave));
FooService client = (FooService) clientFactory.create();
```

### JAX-RS

If using JAX-RS 2, you can use the [portable JaxRs Feature](../brave-jaxrs2/README.md).

You can also configure manually, using this module like so.

CXF JAX-RS server setup:

```java
JAXRSServerFactoryBean serverFactory = new JAXRSServerFactoryBean();
serverFactory.setServiceBeans(new RestFooService());
serverFactory.setAddress("http://localhost:9001/");
serverFactory.getInInterceptors().add(BraveServerInInterceptor.create(brave));
serverFactory.getOutInterceptors().add(BraveServerOutInterceptor.create(brave));
serverFactory.create();
```

CXF JAX-RS client setup:

```java
JAXRSClientFactoryBean clientFactory = new JAXRSClientFactoryBean();
clientFactory.setAddress("http://localhost:9001/");
clientFactory.setServiceClass(FooService.class);
clientFactory.getInInterceptors().add(BraveClientInInterceptor.create(brave));
clientFactory.getOutInterceptors().add(BraveClientOutInterceptor.create(brave));
FooService client = (FooService) clientFactory.create();
```

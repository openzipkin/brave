# brave-cxf3

This component is deprecated and will no longer be published after Brave 4.1

Please use CXF's [built-in Brave tracing integration](https://cwiki.apache.org/confluence/display/CXF20DOC/Using+OpenZipkin+Brave) instead.

## Migration to CXF integration with Brave tracing
The migration from `brave-cxf3` to Apache CXF's integration with Brave tracing is in most cases seamless and straightforward procedure.

### Dependencies

    <dependency>
        <groupId>org.apache.cxf</groupId>
        <artifactId>cxf-integration-tracing-brave</artifactId>
        <version>3.x.y</version>
    </dependency>		

Where `3.x.y` is your Apache CXF version.

### Configuration
The configuration is split into two parts:
 - JAX-WS server-side and client-side
 - JAX-RS server-side and client-side

In most cases there is a dedicated feature provided by Apache CXF so there is no need to deal with interceptors or filters directly (however you still have an option to use those if needed).

### Migrating JAX-WS Configuration
Please use `BraveFeature` from `org.apache.cxf.tracing.brave` package for adding Brave tracing support to your JAX-WS web services, for example:

    JaxWsServerFactoryBean serverFactory = new JaxWsServerFactoryBean();
    serverFactory.setAddress("http://localhost:9000/test");
    serverFactory.setServiceClass(FooService.class);
    serverFactory.setServiceBean(fooServiceImplementation);
    serverFactory.getFeatures().add(new BraveFeature(brave));
    serverFactory.create();

Similarly, please use `BraveClientFeature` from `org.apache.cxf.tracing.brave` package for adding Brave tracing support to your JAX-WS service clients, for example:

    JAXRSClientFactoryBean clientFactory = new JAXRSClientFactoryBean();
    clientFactory.setAddress("http://localhost:9001/");
    clientFactory.setServiceClass(FooService.class);
    clientFactory.getFeatures().add(new BraveClientFeature(brave));
    FooService client = (FooService) clientFactory.create();

### Migrating JAX-RS Configuration
Please use `BraveFeature` from `org.apache.cxf.tracing.brave.jaxrs` package for adding Brave tracing support to your JAX-RS services, for example:

    JAXRSServerFactoryBean serverFactory = new JAXRSServerFactoryBean();
    serverFactory.setServiceBeans(new RestFooService());
    serverFactory.setAddress("http://localhost:9001/");
    serverFactory.getFeatures().add(new BraveFeature(brave));
    serverFactory.create();

  Please use `BraveClientFeature` from `org.apache.cxf.tracing.brave.jaxrs` package for adding Brave tracing support to your JAX-RS service clients, for example:

    JAXRSClientFactoryBean clientFactory = new JAXRSClientFactoryBean();
    clientFactory.setAddress("http://localhost:9001/");
    clientFactory.setServiceClass(FooService.class);
    clientFactory.getFeatures().add(new BraveClientFeature(brave));
    client = (FooService) clientFactory.create()

### Where to find more details
For more use cases and examples please consult Apache CXF's [built-in Brave tracing integration](https://cwiki.apache.org/confluence/display/CXF20DOC/Using+OpenZipkin+Brave).

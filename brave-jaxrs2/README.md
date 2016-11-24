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

For convenience there is also a JAX-RS Feature BraveTraceFeature that can be added to clients as well as services.

## Usage ##

For server side setup, you simply need to tell JAX-RS 2.x to scan the com.github.kristofa.brave.jaxrs2 package, and
the container filters will be picked up and registered automatically.

for Resteasy 3 and Jersey 2 examples check corresponding modules documentation.

If you can not use auto discovery then you can add the feature to the service manually.

For example in an Application class this looks like:

	public class MyApplication extends Application {

	    public Set<Object> getSingletons() {
			Brave brave = new Brave.Builder().build();
       		BraveTraceFeature traceFeature = new BraveTraceFeature(brave);
      		return new HashSet<>(Arrays.asList(traceFeature));
    	}
    
	}
	
For CXF it looks like:

	JAXRSServerFactoryBean factory = new JAXRSServerFactoryBean();
	...
	factory.setProviders(Arrays.asList(new BraveTraceFeature(brave)));
	factory.create();

For client side setup, you just have to register the BraveTraceFeature with your JAX-RS 2 client before you make your request:

    Client client = ClientBuilder.newBuilder().register(new BraveTraceFeature(brave)).build();

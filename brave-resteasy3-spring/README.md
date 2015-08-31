# brave-resteasy3-spring #

The brave-resteasy3-spring module provides Resteasy 3.x client and server support which will allow Brave to be used with any
existing Resteasy 3.x application with minimal configuration. As both Jersey 2 and Resteasy 3 are both based on JAX-RS 2 
common part is in `brave-jaxrs2` module

## Usage ##

For server side setup, you simply need to tell Spring to scan the `com.github.kristofa.brave.resteasy` package and
the container filters will be picked up and registered automatically.

For client side setup, you just have to register the client filters with your Jersey client before you make your request.

It should look something like:

    Client client = ClientBuilder.newClient();
    client.register(myBraveClientRequestFilter);
    client.register(myBraveClientResponseFilter);


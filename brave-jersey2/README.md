# brave-jersey2 #

The brave-jersey2 module provides Jersey 2.x client and server support which will allow Brave to be used with any
existing Jersey 2.x application with minimal configuration.

The module contains 4 filters:

*   `BraveContainerRequestFilter`  - Intercepts incoming container requests and extracts any trace information from
the request header. Also sends sr annotations.
*   `BraveContainerResponseFilter` - Intercepts outgoing container responses and sends ss annotations.
*   `BraveClientRequestFilter` - Intercepts Jersey client requests and adds or forwards tracing information in the header.
Also sends cs annotations.
*   `BraveClientResponseFilter` - Intercepts Jersey client responses and sends cr annotations. Also submits the completed span.

## Usage ##

For server side setup, you simply need to tell Jersey to scan the com.github.kristofa.brave.jersey2 package, and
the container filters will be picked up and registered automatically.

In your web.xml:
    <init-param>
        <param-name>jersey.config.server.provider.packages</param-name>
        <param-value>
            my.existing.packages,com.github.kristofa.brave.jersey2
        </param-value>
    </init-param>

For client side setup, you just have to register the client filters with your Jersey client before you make your request.

It should look something like:

    Client client = ClientBuilder.newClient();
    client.register(myBraveClientRequestFilter);
    client.register(myBraveClientResponseFilter);




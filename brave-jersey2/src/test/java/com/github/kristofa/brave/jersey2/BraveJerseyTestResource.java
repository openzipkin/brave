package com.github.kristofa.brave.jersey2;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

@Path("/brave-jersey2")
public class BraveJerseyTestResource {

    @GET
    @Path("test")
    public Response testEndpoint() {
        return Response.status(200).build();
    }
}

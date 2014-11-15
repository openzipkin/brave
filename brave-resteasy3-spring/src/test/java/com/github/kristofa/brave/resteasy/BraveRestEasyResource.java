package com.github.kristofa.brave.resteasy;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

@Path("/brave-resteasy")
public interface BraveRestEasyResource {

    @Path("/a")
    @GET
    public Response a() throws InterruptedException;

}

package com.github.kristofa.brave.resteasy3;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import org.springframework.stereotype.Repository;

@Repository
@Path("/brave-resteasy")
public class BraveRestEasyResourceImpl implements BraveRestEasyResource {

    @Override
    @Path("/a")
    @GET
    public Response a() throws InterruptedException {
        Thread.sleep(250);
        return Response.status(200).build();

    }

}

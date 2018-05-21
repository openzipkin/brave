package brave.jersey.server;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

/**
 * Resource that has a class level Path annotation, but no Path at the method level.
 */
@Path("/example")
public class TestResourceNoPathOnMethod {
    @GET
    public Response example() {
        return Response.ok().build();
    }

    @GET
    @Path("nested")
    public Response nested() {
        return Response.ok().build();
    }
}

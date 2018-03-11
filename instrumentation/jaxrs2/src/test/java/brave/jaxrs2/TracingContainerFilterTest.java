package brave.jaxrs2;

import brave.Tracing;
import brave.http.HttpTracing;
import java.io.IOException;
import java.net.URI;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TracingContainerFilterTest {
  Tracing tracing = Tracing.newBuilder().build();
  TracingContainerFilter filter =
      new TracingContainerFilter(HttpTracing.create(tracing), ContainerParser.NOOP);
  @Mock ContainerRequestContext context;
  @Mock UriInfo uriInfo;
  @Mock ResourceInfo resourceInfo;

  @After public void close() {
    Tracing.current().close();
  }

  @GET
  @Path("foo")
  public Response get() {
    return Response.status(200).build();
  }

  @GET
  @Path("async")
  public void async(@Suspended AsyncResponse response) throws IOException {
  }

  @Before public void basic() {
    when(context.getMethod()).thenReturn("GET");
    when(context.getUriInfo()).thenReturn(uriInfo);
    when(uriInfo.getRequestUri()).thenReturn(URI.create("/foo"));
  }

  @Test public void setsSpanInScope_methodWithoutSuspendedParam() throws NoSuchMethodException {
    filter.resourceInfo = resourceInfo;
    when(resourceInfo.getResourceMethod()).thenReturn(getClass().getMethod("get"));
    filter.filter(context);

    assertThat(tracing.currentTraceContext().get())
        .isNotNull();
  }

  @Test public void doesntSetSpanInScope_suspendedMethod() throws NoSuchMethodException {
    filter.resourceInfo = resourceInfo;
    when(resourceInfo.getResourceMethod()).thenReturn(
        getClass().getMethod("async", AsyncResponse.class));
    filter.filter(context);

    assertThat(tracing.currentTraceContext().get())
        .isNull();
  }

  /**
   * If we don't know the resource info, we don't know if a method is async or not. We can't set a
   * span in scope as it might leak.
   */
  @Test public void doesntSetSpanInScope_unknownMethod() {
    filter.filter(context);

    assertThat(tracing.currentTraceContext().get())
        .isNull();
  }
}

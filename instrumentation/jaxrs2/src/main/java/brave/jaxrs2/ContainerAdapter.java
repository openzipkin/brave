package brave.jaxrs2;

import brave.http.HttpServerAdapter;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ResourceInfo;

/**
 * This can also parse the resource info of the method.
 *
 * <p>For example, the below adds the java method name as the span name:
 * <pre>{@code
 * TracingFeature.create(httpTracing.toBuilder().serverParser(new HttpServerParser() {
 *   @Override public <Req> String spanName(HttpAdapter<Req, ?> adapter, Req req) {
 *     Method method = ((ContainerAdapter) adapter).resourceMethod(req);
 *     return method.getName().toLowerCase();
 *   }
 * }).build());
 * }</pre>
 */
public final class ContainerAdapter
    extends HttpServerAdapter<ContainerRequestContext, ContainerResponseContext> {
  @Override public String method(ContainerRequestContext request) {
    return request.getMethod();
  }

  @Override public String path(ContainerRequestContext request) {
    return request.getUriInfo().getRequestUri().getPath();
  }

  @Override public String url(ContainerRequestContext request) {
    return request.getUriInfo().getRequestUri().toString();
  }

  @Override public String requestHeader(ContainerRequestContext request, String name) {
    return request.getHeaderString(name);
  }

  @Override public Integer statusCode(ContainerResponseContext response) {
    return response.getStatus();
  }

  @Nullable public final <Req> Method resourceMethod(Req request) {
    ResourceInfo resourceInfo = resourceInfo(request);
    return resourceInfo != null ? resourceInfo.getResourceMethod() : null;
  }

  @Nullable public final <Req> Class<?> resourceClass(Req request) {
    ResourceInfo resourceInfo = resourceInfo(request);
    return resourceInfo != null ? resourceInfo.getResourceClass() : null;
  }

  @Nullable public final <Req> ResourceInfo resourceInfo(Req request) {
    if (!(request instanceof ContainerRequestContext)) return null;
    return (ResourceInfo) ((ContainerRequestContext) request).getProperty(
        ResourceInfo.class.getName());
  }
}

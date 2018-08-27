package brave.vertx.web;

import brave.vertx.web.TracingRoutingContextHandler.Adapter;
import brave.vertx.web.TracingRoutingContextHandler.Route;
import io.vertx.core.http.HttpServerResponse;
import java.lang.reflect.Proxy;
import org.junit.After;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TracingRoutingContextHandlerAdapterTest {
  ThreadLocal<Route> currentRoute = new ThreadLocal<>();
  Adapter adapter = new Adapter(currentRoute);

  @After public void clear() {
    currentRoute.remove();
  }

  @Test public void methodFromResponse() {
    HttpServerResponse response = dummyResponse();

    currentRoute.set(new Route("GET", null));
    assertThat(adapter.methodFromResponse(response))
        .isEqualTo("GET");
  }

  @Test public void route_emptyByDefault() {
    HttpServerResponse response = dummyResponse();

    currentRoute.set(new Route("GET", null));
    assertThat(adapter.route(response)).isEmpty();
  }

  @Test public void route() {
    HttpServerResponse response = dummyResponse();

    currentRoute.set(new Route("GET", "/users/:userID"));
    assertThat(adapter.route(response))
        .isEqualTo("/users/:userID");
  }

  /** In JRE 1.8, mockito crashes with 'Mockito cannot mock this class' */
  HttpServerResponse dummyResponse() {
    return (HttpServerResponse) Proxy.newProxyInstance(
        getClass().getClassLoader(),
        new Class[] {HttpServerResponse.class},
        (proxy, method, methodArgs) -> {
          throw new UnsupportedOperationException(
              "Unsupported method: " + method.getName());
        });
  }
}

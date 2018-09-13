package brave.vertx.web;

import io.vertx.core.http.HttpServerResponse;
import java.lang.reflect.Proxy;
import org.junit.After;
import org.junit.Test;

import static brave.test.util.ClassLoaders.assertRunIsUnloadable;
import static org.assertj.core.api.Assertions.assertThat;

public class VertxHttpServerAdapterTest {
  VertxHttpServerAdapter adapter = new VertxHttpServerAdapter();

  @After public void clear() {
    VertxHttpServerAdapter.METHOD_AND_PATH.remove();
  }

  @Test public void methodFromResponse() {
    HttpServerResponse response = dummyResponse();

    VertxHttpServerAdapter.setCurrentMethodAndPath("GET", null);

    assertThat(adapter.methodFromResponse(response))
        .isEqualTo("GET");
  }

  @Test public void route_emptyByDefault() {
    HttpServerResponse response = dummyResponse();

    VertxHttpServerAdapter.setCurrentMethodAndPath("GET", null);

    assertThat(adapter.route(response)).isEmpty();
  }

  @Test public void route() {
    HttpServerResponse response = dummyResponse();

    VertxHttpServerAdapter.setCurrentMethodAndPath("GET", "/users/:userID");

    assertThat(adapter.route(response))
        .isEqualTo("/users/:userID");
  }

  @Test public void setCurrentMethodAndPath_doesntPreventClassUnloading() {
    assertRunIsUnloadable(MethodFromResponse.class, getClass().getClassLoader());
  }

  static class MethodFromResponse implements Runnable {
    final VertxHttpServerAdapter adapter = new VertxHttpServerAdapter();

    @Override public void run() {
      VertxHttpServerAdapter.setCurrentMethodAndPath("GET", null);
      adapter.methodFromResponse(null);
    }
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

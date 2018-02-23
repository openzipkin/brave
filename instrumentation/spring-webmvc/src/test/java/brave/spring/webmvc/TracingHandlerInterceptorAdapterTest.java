package brave.spring.webmvc;

import brave.spring.webmvc.TracingHandlerInterceptor.Adapter;
import brave.spring.webmvc.TracingHandlerInterceptor.DecoratedHttpServletResponse;
import javax.servlet.http.HttpServletResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(MockitoJUnitRunner.class)
public class TracingHandlerInterceptorAdapterTest {
  Adapter adapter = new Adapter();
  @Mock HttpServletResponse response;

  @Test public void methodFromResponse() {
    assertThat(adapter.methodFromResponse(
        new DecoratedHttpServletResponse(response, "GET", null)))
        .isEqualTo("GET");
  }

  @Test public void route_emptyByDefault() {
    assertThat(adapter.route(new DecoratedHttpServletResponse(response, "GET", null)))
        .isEmpty();
  }

  @Test public void route() {
    assertThat(adapter.route(new DecoratedHttpServletResponse(response, "GET", "/users/:userID")))
        .isEqualTo("/users/:userID");
  }
}

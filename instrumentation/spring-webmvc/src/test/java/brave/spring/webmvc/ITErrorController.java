package brave.spring.webmvc;

import brave.Tracer;
import brave.test.http.ITHttpServer;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class ITErrorController extends ITSpringServlet {

  @Override List<Class<?>> configurationClasses() {
    return Arrays.asList(AsyncHandlerInterceptorConfig.class, ExceptionThrowingController.class);
  }

  @Controller
  static class ExceptionThrowingController {
    @Autowired Tracer tracer; // just showing you can.

    @RequestMapping("/")
    public void throwException() {
      throw new RuntimeException("Throwing exception");
    }

    @RequestMapping(value = "/test_bad_request")
    public ResponseEntity<Void> processFail() {
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
  }

  /** possible dupe of {@link ITHttpServer#addsErrorTagOnException()} */
  @Test
  // wonder if the name is right?
  public void should_not_create_a_span_for_error_controller() throws Exception {
    assertThat(get("/").code())
        .isEqualTo(500);

    Span span = takeSpan();
    assertThat(span.tags()).containsOnly(
        // notice. there's no http code. whoops?
        entry("http.method", "GET"),
        entry("http.path", "/"),
        entry("error", "Throwing exception")
    );
    // the cleanup always checks for extra spans, so it will fail if there was extra
  }

  /** possible dupe of {@link ITHttpServer#addsStatusCode_badRequest()} */
  @Test
  public void should_create_spans_for_endpoint_returning_unsuccessful_result() throws Exception {
    assertThat(get("/test_bad_request").code())
        .isEqualTo(400);

    Span span = takeSpan();
    assertThat(span.tags()).containsOnly(
        entry("http.method", "GET"),
        entry("http.path", "/test_bad_request"),
        entry("http.status_code", "400"),
        entry("error", "400")
    );
  }
}

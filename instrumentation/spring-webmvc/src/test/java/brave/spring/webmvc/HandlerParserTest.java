package brave.spring.webmvc;

import brave.SpanCustomizer;
import javax.servlet.http.HttpServletRequest;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class HandlerParserTest {
  HttpServletRequest request = mock(HttpServletRequest.class);
  SpanCustomizer customizer = mock(SpanCustomizer.class);
  HandlerParser parser = new HandlerParser();
  TestController controller = new TestController();

  @Controller static class TestController {
    @RequestMapping(value = "/items/{itemId}")
    public ResponseEntity<String> items(@PathVariable String itemId) {
      return new ResponseEntity<>(itemId, HttpStatus.OK);
    }
  }

  /** For Spring WebMVC 3.1+ */
  @Test public void preHandle_HandlerMethod_addsClassAndMethodTags() throws Exception {
    parser.preHandle(
        request,
        new HandlerMethod(controller, TestController.class.getMethod("items", String.class)),
        customizer
    );

    verify(customizer).tag("mvc.controller.class", "TestController");
    verify(customizer).tag("mvc.controller.method", "items");
    verifyNoMoreInteractions(request, customizer);
  }

  /** For Spring WebMVC 2.5 */
  @Test public void preHandle_Handler_addsClassTag() {
    parser.preHandle(request, controller, customizer);

    verify(customizer).tag("mvc.controller.class", "TestController");
    verifyNoMoreInteractions(request, customizer);
  }

  @Test public void preHandle_NOOP_addsNothing() {
    HandlerParser.NOOP.preHandle(request, controller, customizer);

    verifyNoMoreInteractions(request, customizer);
  }
}

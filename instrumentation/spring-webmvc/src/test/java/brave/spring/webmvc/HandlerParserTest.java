/*
 * Copyright 2013-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
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

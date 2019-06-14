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
import javax.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE;

public class SpanCustomizingAsyncHandlerInterceptorTest {
  SpanCustomizingAsyncHandlerInterceptor interceptor;
  TestController controller = new TestController();

  HttpServletRequest request = mock(HttpServletRequest.class);
  HttpServletResponse response = mock(HttpServletResponse.class);
  SpanCustomizer span = mock(SpanCustomizer.class);
  HandlerParser parser = mock(HandlerParser.class);

  @Before
  public void setup() {
    interceptor = new SpanCustomizingAsyncHandlerInterceptor();
    interceptor.handlerParser = parser;
  }

  @Test
  public void preHandle_parses() {
    when(request.getAttribute("brave.SpanCustomizer")).thenReturn(span);

    interceptor.preHandle(request, response, controller);

    verify(request).getAttribute("brave.SpanCustomizer");
    verify(parser).preHandle(request, controller, span);

    verifyNoMoreInteractions(request, response, parser, span);
  }

  @Test
  public void afterCompletion_addsHttpRouteAttribute() {
    when(request.getAttribute("brave.SpanCustomizer")).thenReturn(span);
    when(request.getAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE)).thenReturn("/items/{itemId}");

    interceptor.afterCompletion(request, response, controller, null);

    verify(request).getAttribute("brave.SpanCustomizer");
    verify(request).getAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE);
    verify(request).setAttribute("http.route", "/items/{itemId}");

    verifyNoMoreInteractions(request, response, parser, span);
  }

  @Test
  public void afterCompletion_addsHttpRouteAttribute_coercesNullToEmpty() {
    when(request.getAttribute("brave.SpanCustomizer")).thenReturn(span);

    interceptor.afterCompletion(request, response, controller, null);

    verify(request).getAttribute("brave.SpanCustomizer");
    verify(request).getAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE);
    verify(request).setAttribute("http.route", "");

    verifyNoMoreInteractions(request, response, parser, span);
  }

  @Test
  public void preHandle_nothingWhenNoSpanAttribute() {
    interceptor.preHandle(request, response, controller);

    verify(request).getAttribute("brave.SpanCustomizer");
    verifyNoMoreInteractions(request, request, parser, span);
  }

  @Controller
  static class TestController {
    @RequestMapping(value = "/items/{itemId}")
    public ResponseEntity<String> items(@PathVariable("itemId") String itemId) {
      return new ResponseEntity<>(itemId, HttpStatus.OK);
    }
  }
}

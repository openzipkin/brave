/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.spring.webmvc;

import brave.SpanCustomizer;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

  @BeforeEach
  public void setup() {
    interceptor = new SpanCustomizingAsyncHandlerInterceptor();
    interceptor.handlerParser = parser;
  }

  @Test void preHandle_parses() {
    when(request.getAttribute("brave.SpanCustomizer")).thenReturn(span);

    interceptor.preHandle(request, response, controller);

    verify(request).getAttribute("brave.SpanCustomizer");
    verify(parser).preHandle(request, controller, span);

    verifyNoMoreInteractions(request, response, parser, span);
  }

  @Test void afterCompletion_addsHttpRouteAttribute() {
    when(request.getAttribute("brave.SpanCustomizer")).thenReturn(span);
    when(request.getAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE)).thenReturn("/items/{itemId}");

    interceptor.afterCompletion(request, response, controller, null);

    verify(request).getAttribute("brave.SpanCustomizer");
    verify(request).getAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE);
    verify(request).setAttribute("http.route", "/items/{itemId}");

    verifyNoMoreInteractions(request, response, parser, span);
  }

  @Test void afterCompletion_addsHttpRouteAttribute_coercesNullToEmpty() {
    when(request.getAttribute("brave.SpanCustomizer")).thenReturn(span);

    interceptor.afterCompletion(request, response, controller, null);

    verify(request).getAttribute("brave.SpanCustomizer");
    verify(request).getAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE);
    verify(request).setAttribute("http.route", "");

    verifyNoMoreInteractions(request, response, parser, span);
  }

  @Test void preHandle_nothingWhenNoSpanAttribute() {
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

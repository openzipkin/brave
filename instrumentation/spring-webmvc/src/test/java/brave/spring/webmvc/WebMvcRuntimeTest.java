/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.spring.webmvc;

import brave.http.HttpTracing;
import brave.spring.webmvc.WebMvcRuntime.WebMvc25;
import brave.spring.webmvc.WebMvcRuntime.WebMvc31;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebMvcRuntimeTest {

  @Test void findWebMvcRuntime_HandlerMethod_exists() {
    assertThat(WebMvcRuntime.findWebMvcRuntime())
      .isInstanceOf(WebMvc31.class);
  }

  @Test void WebMvc31_isHandlerMethod() {
    HandlerMethod handlerMethod = mock(HandlerMethod.class);

    assertThat(new WebMvc31().isHandlerMethod(handlerMethod))
      .isTrue();
  }

  /** Due to HandlerMethod being only present after 3.1, we can't look up the class in 2.5 */
  @Test void WebMvc25_isHandlerMethod_isFalse() {
    HandlerMethod handlerMethod = mock(HandlerMethod.class);

    assertThat(new WebMvc25().isHandlerMethod(handlerMethod))
      .isFalse();
  }

  /** Spring 3+ can get beans by type, so use that! */
  @Test void WebMvc31_httpTracing_byType() {
    ApplicationContext context = mock(ApplicationContext.class);

    new WebMvc31().httpTracing(context);

    verify(context).getBean(HttpTracing.class);
    verifyNoMoreInteractions(context);
  }

  /** Spring 2.5 cannot get beans by type, so fallback to name */
  @Test void WebMvc25_httpTracing_byName() {
    ApplicationContext context = mock(ApplicationContext.class);
    when(context.containsBean("httpTracing")).thenReturn(true);
    when(context.getBean("httpTracing")).thenReturn(mock(HttpTracing.class));

    new WebMvc25().httpTracing(context);

    verify(context).containsBean("httpTracing");
    verify(context).getBean("httpTracing");
    verifyNoMoreInteractions(context);
  }

  @Test void WebMvc25_httpTracing_whenWrongType() {
    ApplicationContext context = mock(ApplicationContext.class);
    when(context.containsBean("httpTracing")).thenReturn(true);
    when(context.getBean("httpTracing")).thenReturn("foo");

    assertThatThrownBy(() -> new WebMvc25().httpTracing(context))
      .isInstanceOf(NoSuchBeanDefinitionException.class);
  }

  @Test void WebMvc25_httpTracing_whenDoesntExist() {
    ApplicationContext context = mock(ApplicationContext.class);
    when(context.containsBean("httpTracing")).thenReturn(false);

    assertThatThrownBy(() -> new WebMvc25().httpTracing(context))
      .isInstanceOf(NoSuchBeanDefinitionException.class);
  }
}

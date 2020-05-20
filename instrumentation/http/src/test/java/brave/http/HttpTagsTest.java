/*
 * Copyright 2013-2020 The OpenZipkin Authors
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
package brave.http;

import brave.SpanCustomizer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** This only tests things not already covered in {@code brave.TagTest} */
@RunWith(MockitoJUnitRunner.class)
public class HttpTagsTest {
  @Mock SpanCustomizer span;
  @Mock HttpRequest request;
  @Mock HttpResponse response;

  @Test public void method() {
    when(request.method()).thenReturn("GET");
    HttpTags.METHOD.tag(request, span);

    verify(span).tag("http.method", "GET");
  }

  @Test public void method_null() {
    HttpTags.METHOD.tag(request, span);

    verifyNoMoreInteractions(span);
  }

  @Test public void path() {
    when(request.path()).thenReturn("/objects/abcd-ff");
    HttpTags.PATH.tag(request, span);

    verify(span).tag("http.path", "/objects/abcd-ff");
  }

  @Test public void path_null() {
    HttpTags.PATH.tag(request, span);

    verifyNoMoreInteractions(span);
  }

  @Test public void route() {
    when(request.route()).thenReturn("/items/:itemId");
    HttpTags.ROUTE.tag(request, span);

    verify(span).tag("http.route", "/items/:itemId");
  }

  @Test public void route_null() {
    HttpTags.ROUTE.tag(request, span);

    verifyNoMoreInteractions(span);
  }

  @Test public void url() {
    when(request.url()).thenReturn("https://zipkin.io");
    HttpTags.URL.tag(request, span);

    verify(span).tag("http.url", "https://zipkin.io");
  }

  @Test public void url_null() {
    HttpTags.URL.tag(request, span);

    verifyNoMoreInteractions(span);
  }

  @Test public void requestHeader() {
    when(request.header("User-Agent")).thenReturn("Mozilla/5.0");
    HttpTags.requestHeader("User-Agent").tag(request, span);

    verify(span).tag("User-Agent", "Mozilla/5.0");
  }

  @Test public void requestHeader_renamed() {
    when(request.header("User-Agent")).thenReturn("Mozilla/5.0");
    HttpTags.requestHeader("http.user_agent", "User-Agent").tag(request, span);

    verify(span).tag("http.user_agent", "Mozilla/5.0");
  }

  @Test public void requestHeader_null() {
    HttpTags.requestHeader("User-Agent").tag(request, span);

    verifyNoMoreInteractions(span);
  }

  @Test public void status_code() {
    when(response.statusCode()).thenReturn(200);
    HttpTags.STATUS_CODE.tag(response, span);

    verify(span).tag("http.status_code", "200");
  }

  @Test public void status_code_zero() {
    HttpTags.STATUS_CODE.tag(response, span);

    verifyNoMoreInteractions(span);
  }

  @Test public void status_code_invalid() {
    when(response.statusCode()).thenReturn(600);
    HttpTags.STATUS_CODE.tag(response, span);

    verifyNoMoreInteractions(span);
  }
}

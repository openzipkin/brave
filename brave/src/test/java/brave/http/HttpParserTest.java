package brave.http;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import zipkin.TraceKeys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpParserTest {
  @Mock HttpClientAdapter<Object, Object> adapter;
  @Mock brave.Span span;
  Object request = new Object();
  Object response = new Object();
  HttpParser parser = new HttpParser();

  @Test public void spanName_isMethod() {
    when(adapter.method(request)).thenReturn("GET");

    assertThat(parser.spanName(adapter, request))
        .isEqualTo("GET");
  }

  @Test public void requestTags_addsPath() {
    when(adapter.path(request)).thenReturn("/foo");

    parser.requestTags(adapter, request, span);

    verify(span).tag(TraceKeys.HTTP_PATH, "/foo");
  }

  @Test public void requestTags_doesntCrashOnNullPath() {
    parser.requestTags(adapter, request, span);

    verify(span, never()).tag(TraceKeys.HTTP_PATH, null);
  }

  @Test public void responseTags_tagsErrorOnResponseCode() {
    when(adapter.statusCode(response)).thenReturn(400);

    parser.responseTags(adapter, response, null, span);

    verify(span).tag("error", "400");
  }

  @Test public void responseTags_tagsErrorFromException() {
    parser.responseTags(adapter, response, new RuntimeException("drat"), span);

    verify(span).tag("error", "drat");
  }

  @Test public void responseTags_tagsErrorPrefersExceptionVsResponseCode() {
    when(adapter.statusCode(response)).thenReturn(400);

    parser.responseTags(adapter, response, new RuntimeException("drat"), span);

    verify(span).tag("error", "drat");
  }

  @Test public void responseTags_tagsErrorOnExceptionEvenIfStatusOk() {
    when(adapter.statusCode(response)).thenReturn(200);

    parser.responseTags(adapter, response, new RuntimeException("drat"), span);

    verify(span).tag("error", "drat");
  }
}

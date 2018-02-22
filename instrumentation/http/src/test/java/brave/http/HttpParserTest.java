package brave.http;

import brave.SpanCustomizer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpParserTest {
  @Mock HttpClientAdapter<Object, Object> adapter;
  @Mock SpanCustomizer customizer;
  Object request = new Object();
  Object response = new Object();
  HttpParser parser = new HttpParser();

  @Test public void spanName_isMethod() {
    when(adapter.method(request)).thenReturn("GET");

    assertThat(parser.spanName(adapter, request))
        .isEqualTo("GET"); // note: in practice this will become lowercase
  }

  @Test public void request_addsMethodAndPath() {
    when(adapter.method(request)).thenReturn("GET");
    when(adapter.path(request)).thenReturn("/foo");

    parser.request(adapter, request, customizer);

    verify(customizer).tag("http.method", "GET");
    verify(customizer).tag("http.path", "/foo");
  }

  @Test public void request_doesntCrashOnNullPath() {
    parser.request(adapter, request, customizer);

    verify(customizer, never()).tag("http.path", null);
  }

  @Test public void response_tagsStatusAndErrorOnResponseCode() {
    when(adapter.statusCodeAsInt(response)).thenReturn(400);

    parser.response(adapter, response, null, customizer);

    verify(customizer).tag("http.status_code", "400");
    verify(customizer).tag("error", "400");
  }

  @Test public void response_statusZeroIsNotAnError() {
    when(adapter.statusCodeAsInt(response)).thenReturn(0);

    parser.response(adapter, response, null, customizer);

    verify(customizer, never()).tag("http.status_code", "0");
    verify(customizer, never()).tag("error", "0");
  }

  @Test public void response_tagsErrorFromException() {
    parser.response(adapter, response, new RuntimeException("drat"), customizer);

    verify(customizer).tag("error", "drat");
  }

  @Test public void response_tagsErrorPrefersExceptionVsResponseCode() {
    when(adapter.statusCodeAsInt(response)).thenReturn(400);

    parser.response(adapter, response, new RuntimeException("drat"), customizer);

    verify(customizer).tag("error", "drat");
  }

  @Test public void response_tagsErrorOnExceptionEvenIfStatusOk() {
    when(adapter.statusCodeAsInt(response)).thenReturn(200);

    parser.response(adapter, response, new RuntimeException("drat"), customizer);

    verify(customizer).tag("error", "drat");
  }
}

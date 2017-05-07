package brave.spring.web;

import brave.Span;
import brave.Tracing;
import brave.http.HttpTracing;
import java.net.URI;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.http.HttpRequest;
import zipkin.Endpoint;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TracingClientHttpRequestInterceptorTest {
  TracingClientHttpRequestInterceptor filter =
      new TracingClientHttpRequestInterceptor(HttpTracing.create(Tracing.newBuilder().build()));
  @Mock HttpRequest request;
  @Mock Span span;

  @Test public void parseServerAddress_skipsOnNoop() {
    when(span.isNoop()).thenReturn(true);
    filter.parseServerAddress(request, span);

    verify(span).isNoop();
    verifyNoMoreInteractions(request, span);
  }

  @Test public void parseServerAddress_ipFromHost() {
    when(span.isNoop()).thenReturn(false);
    when(request.getURI()).thenReturn(URI.create("http://127.0.0.1/foo"));
    filter.parseServerAddress(request, span);

    verify(span).remoteEndpoint(Endpoint.builder().serviceName("").ipv4(127 << 24 | 1).build());
  }

  @Test public void parseServerAddress_ipAndPortFromUri() {
    when(span.isNoop()).thenReturn(false);
    when(request.getURI()).thenReturn(URI.create("http://127.0.0.1:9999/foo"));
    filter.parseServerAddress(request, span);

    verify(span).remoteEndpoint(
        Endpoint.builder().serviceName("").ipv4(127 << 24 | 1).port(9999).build());
  }

  @Test public void parseServerAddress_doesntNsLookup() {
    when(span.isNoop()).thenReturn(false);
    when(request.getURI()).thenReturn(URI.create("http://localhost/foo"));
    filter.parseServerAddress(request, span);

    verify(span).isNoop();
    verifyNoMoreInteractions(span);
  }

  @Test public void parseServerAddress_doesntSkipWhenServiceNameIsNotBlank() {
    filter = new TracingClientHttpRequestInterceptor(
        HttpTracing.newBuilder(Tracing.newBuilder().build()).serverName("foo").build()
    );

    when(span.isNoop()).thenReturn(false);
    when(request.getURI()).thenReturn(URI.create("http://localhost/foo"));
    filter.parseServerAddress(request, span);

    verify(span).remoteEndpoint(Endpoint.builder().serviceName("foo").build());
  }
}

package brave.servlet;

import brave.Span;
import javax.servlet.http.HttpServletRequest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import zipkin.Endpoint;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpServletAdapterTest {
  @Mock HttpServletRequest request;
  @Mock Span span;

  @Test public void parseClientAddress_skipOnNoop() {
    when(span.isNoop()).thenReturn(true);
    HttpServletAdapter.parseClientAddress(request, span);

    verify(span).isNoop();
    verifyNoMoreInteractions(request, span);
  }

  @Test public void parseClientAddress_prefersXForwardedFor() {
    when(span.isNoop()).thenReturn(false);
    when(request.getHeader("X-Forwarded-For")).thenReturn("127.0.0.1");
    HttpServletAdapter.parseClientAddress(request, span);

    verify(span).remoteEndpoint(Endpoint.builder().serviceName("").ipv4(127 << 24 | 1).build());
  }

  @Test public void parseClientAddress_readsRemotePort() {
    when(span.isNoop()).thenReturn(false);
    when(request.getHeader("X-Forwarded-For")).thenReturn("127.0.0.1");
    when(request.getRemotePort()).thenReturn(61687);
    HttpServletAdapter.parseClientAddress(request, span);

    verify(span).remoteEndpoint(
        Endpoint.builder().serviceName("").ipv4(127 << 24 | 1).port(61687).build());
  }

  @Test public void parseClientAddress_acceptsRemoteAddr() {
    when(span.isNoop()).thenReturn(false);
    when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    HttpServletAdapter.parseClientAddress(request, span);

    verify(span).remoteEndpoint(Endpoint.builder().serviceName("").ipv4(127 << 24 | 1).build());
  }

  @Test public void parseClientAddress_skipsOnNoIp() {
    when(span.isNoop()).thenReturn(false);
    HttpServletAdapter.parseClientAddress(request, span);

    verify(span).isNoop();
    verifyNoMoreInteractions(span);
  }

  @Test public void parseClientAddress_doesntNsLookup() {
    when(span.isNoop()).thenReturn(false);
    when(request.getHeader("X-Forwarded-For")).thenReturn("localhost");
    when(request.getRemoteAddr()).thenReturn("localhost");
    HttpServletAdapter.parseClientAddress(request, span);

    verify(span).isNoop();
    verifyNoMoreInteractions(span);
  }
}

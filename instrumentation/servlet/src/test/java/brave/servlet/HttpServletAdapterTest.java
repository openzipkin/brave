package brave.servlet;

import javax.servlet.http.HttpServletRequest;
import org.assertj.core.api.AbstractObjectAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import zipkin.Endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpServletAdapterTest {
  HttpServletAdapter adapter = new HttpServletAdapter();
  @Mock HttpServletRequest request;

  @Test public void path_doesntCrashOnNullUrl() {
    assertThat(adapter.path(request))
        .isNull();
  }

  @Test public void path_getRequestURI() {
    when(request.getRequestURI()).thenReturn("/bar");

    assertThat(adapter.path(request))
        .isEqualTo("/bar");
  }

  @Test public void url_derivedFromUrlAndQueryString() {
    when(request.getRequestURL()).thenReturn(new StringBuffer("http://foo:8080/bar"));
    when(request.getQueryString()).thenReturn("hello=world");

    assertThat(adapter.url(request))
        .isEqualTo("http://foo:8080/bar?hello=world");
  }

  @Test public void parseClientAddress_prefersXForwardedFor() {
    when(adapter.requestHeader(request, "X-Forwarded-For")).thenReturn("127.0.0.1");

    assertParsedEndpoint()
        .isEqualTo(Endpoint.builder().serviceName("").ipv4(127 << 24 | 1).build());
  }

  @Test public void parseClientAddress_skipsOnNoIp() {
    assertThat(adapter.parseClientAddress(request, Endpoint.builder()))
        .isFalse();
  }

  @Test public void parseClientAddress_readsRemotePort() {
    when(request.getHeader("X-Forwarded-For")).thenReturn("127.0.0.1");
    when(request.getRemotePort()).thenReturn(61687);

    assertParsedEndpoint()
        .isEqualTo(Endpoint.builder().serviceName("").ipv4(127 << 24 | 1).port(61687).build());
  }

  @Test public void parseClientAddress_acceptsRemoteAddr() {
    when(request.getRemoteAddr()).thenReturn("127.0.0.1");

    assertParsedEndpoint()
        .isEqualTo(Endpoint.builder().serviceName("").ipv4(127 << 24 | 1).build());
  }

  @Test public void parseClientAddress_doesntNsLookup() {
    when(adapter.requestHeader(request, "X-Forwarded-For")).thenReturn("localhost");

    assertThat(adapter.parseClientAddress(request, Endpoint.builder()))
        .isFalse();
  }

  AbstractObjectAssert<?, Endpoint> assertParsedEndpoint() {
    Endpoint.Builder remoteAddress = Endpoint.builder();
    assertThat(adapter.parseClientAddress(request, remoteAddress))
        .isTrue();

    return assertThat(remoteAddress.serviceName("").build());
  }
}

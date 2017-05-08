package brave.http;

import org.assertj.core.api.AbstractObjectAssert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import zipkin.Endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpServerAdapterTest {
  @Mock HttpServerAdapter<Object, Object> adapter;
  Object request = new Object();

  @Before public void callRealMethod() {
    when(adapter.parseClientAddress(eq(request), anyObject())).thenCallRealMethod();
    when(adapter.path(request)).thenCallRealMethod();
  }

  @Test public void path_doesntCrashOnNullUrl() {
    assertThat(adapter.path(request))
        .isNull();
  }

  @Test public void path_derivedFromUrl() {
    when(adapter.url(request)).thenReturn("http://foo:8080/bar?hello=world");

    assertThat(adapter.path(request))
        .isEqualTo("/bar");
  }

  @Test public void parseClientAddress_prefersXForwardedFor() {
    when(adapter.requestHeader(request, "X-Forwarded-For")).thenReturn("127.0.0.1");

    Endpoint.Builder remoteAddress = Endpoint.builder();
    assertThat(adapter.parseClientAddress(request, remoteAddress))
        .isTrue();

    assertThat(remoteAddress.serviceName("").build())
        .isEqualTo(Endpoint.builder().serviceName("").ipv4(127 << 24 | 1).build());
  }

  @Test public void parseClientAddress_skipsOnNoIp() {
    assertThat(adapter.parseClientAddress(request, Endpoint.builder()))
        .isFalse();
  }

  @Test public void parseClientAddress_doesntNsLookup() {
    when(adapter.requestHeader(request, "X-Forwarded-For")).thenReturn("localhost");

    assertThat(adapter.parseClientAddress(request, Endpoint.builder()))
        .isFalse();
  }
}

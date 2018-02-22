package brave.http;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import zipkin2.Endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpServerAdapterTest {
  @Mock HttpServerAdapter<Object, Object> adapter;
  Object request = new Object();
  Object response = new Object();

  @Before public void callRealMethod() {
    when(adapter.parseClientAddress(eq(request), isA(Endpoint.Builder.class))).thenCallRealMethod();
    when(adapter.path(request)).thenCallRealMethod();
    when(adapter.statusCodeAsInt(response)).thenCallRealMethod();
  }

  @Test public void path_doesntCrashOnNullUrl() {
    assertThat(adapter.path(request))
        .isNull();
  }

  @Test public void statusCodeAsInt_callsStatusCodeByDefault() {
    when(adapter.statusCode(response)).thenReturn(400);

    assertThat(adapter.statusCodeAsInt(response))
        .isEqualTo(400);
  }

  @Test public void path_derivedFromUrl() {
    when(adapter.url(request)).thenReturn("http://foo:8080/bar?hello=world");

    assertThat(adapter.path(request))
        .isEqualTo("/bar");
  }

  @Test public void parseClientAddress_prefersXForwardedFor() {
    when(adapter.requestHeader(request, "X-Forwarded-For")).thenReturn("127.0.0.1");

    Endpoint.Builder remoteAddress = Endpoint.newBuilder();
    assertThat(adapter.parseClientAddress(request, remoteAddress))
        .isTrue();

    assertThat(remoteAddress.build())
        .isEqualTo(Endpoint.newBuilder().ip("127.0.0.1").build());
  }

  @Test public void parseClientAddress_skipsOnNoIp() {
    assertThat(adapter.parseClientAddress(request, Endpoint.newBuilder()))
        .isFalse();
  }

  @Test public void parseClientAddress_doesntNsLookup() {
    when(adapter.requestHeader(request, "X-Forwarded-For")).thenReturn("localhost");

    assertThat(adapter.parseClientAddress(request, Endpoint.newBuilder()))
        .isFalse();
  }
}

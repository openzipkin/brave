package brave.http;

import brave.Span;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpServerAdapterTest {
  @Mock HttpServerAdapter<Object, Object> adapter;
  @Mock Span span;
  Object request = new Object();
  Object response = new Object();

  @Before public void callRealMethod() {
    doCallRealMethod().when(adapter).parseClientIpAndPort(eq(request), isA(Span.class));
    when(adapter.path(request)).thenCallRealMethod();
    when(adapter.statusCodeAsInt(response)).thenCallRealMethod();
    when(adapter.parseClientIpFromXForwardedFor(request, span)).thenCallRealMethod();
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

  @Test public void parseClientIpAndPort_prefersXForwardedFor() {
    when(adapter.requestHeader(request, "X-Forwarded-For")).thenReturn("1.2.3.4");

    adapter.parseClientIpAndPort(request, span);

    verify(span).remoteIpAndPort("1.2.3.4", 0);
    verifyNoMoreInteractions(span);
  }

  @Test public void parseClientIpAndPort_skipsOnNoIp() {
    adapter.parseClientIpAndPort(request, span);

    verifyNoMoreInteractions(span);
  }
}

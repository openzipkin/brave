package brave.httpasyncclient;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.apache.http.HttpHost;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TracingHttpAsyncClientBuilderTest {
  @Mock HttpRequestWrapper request;
  @Mock brave.Span span;

  @Test public void parseTargetAddress_skipsOnNoop() {
    when(span.isNoop()).thenReturn(true);

    TracingHttpAsyncClientBuilder.parseTargetAddress(request, span);

    verify(span).isNoop();
    verifyNoMoreInteractions(span);
  }

  @Test public void parseTargetAddress_prefersAddress() throws UnknownHostException {
    when(span.isNoop()).thenReturn(false);
    when(span.remoteIpAndPort("1.2.3.4", -1)).thenReturn(true);
    when(request.getTarget()).thenReturn(
        new HttpHost(InetAddress.getByName("1.2.3.4"), "3.4.5.6", -1, "http"));

    TracingHttpAsyncClientBuilder.parseTargetAddress(request, span);

    verify(span).isNoop();
    verify(span).remoteIpAndPort("1.2.3.4", -1);
    verifyNoMoreInteractions(span);
  }

  @Test public void parseTargetAddress_acceptsHostname() {
    when(span.isNoop()).thenReturn(false);
    when(request.getTarget()).thenReturn(new HttpHost("1.2.3.4"));

    TracingHttpAsyncClientBuilder.parseTargetAddress(request, span);

    verify(span).isNoop();
    verify(span).remoteIpAndPort("1.2.3.4", -1);
    verifyNoMoreInteractions(span);
  }

  @Test public void parseTargetAddress_IpAndPortFromHost() {
    when(span.isNoop()).thenReturn(false);
    when(span.remoteIpAndPort("1.2.3.4", 9999)).thenReturn(true);

    when(request.getTarget()).thenReturn(new HttpHost("1.2.3.4", 9999));

    TracingHttpAsyncClientBuilder.parseTargetAddress(request, span);

    verify(span).isNoop();
    verify(span).remoteIpAndPort("1.2.3.4", 9999);
    verifyNoMoreInteractions(span);
  }
}

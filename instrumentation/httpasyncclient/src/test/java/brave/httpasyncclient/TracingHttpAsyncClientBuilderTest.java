package brave.httpasyncclient;

import brave.Span;
import brave.Tracing;
import brave.http.HttpTracing;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.apache.http.HttpHost;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import zipkin.Endpoint;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@RunWith(MockitoJUnitRunner.class)
public class TracingHttpAsyncClientBuilderTest {
  TracingHttpAsyncClientBuilder filter =
      new TracingHttpAsyncClientBuilder(HttpTracing.create(Tracing.newBuilder().build()));
  @Mock Span span;

  @Test public void parseServerAddress_skipsOnNoop() {
    filter.parseServerAddress(HttpHost.create("http://localhost"), span);

    verifyNoMoreInteractions(span);
  }

  @Test public void parseServerAddress_prefersAddress() throws UnknownHostException {
    filter.parseServerAddress(new HttpHost(InetAddress.getByName("127.0.0.1")), span);

    verify(span).remoteEndpoint(Endpoint.builder().serviceName("").ipv4(127 << 24 | 1).build());
  }

  @Test public void parseServerAddress_acceptsHostname() {
    filter.parseServerAddress(HttpHost.create("http://127.0.0.1"), span);

    verify(span).remoteEndpoint(Endpoint.builder().serviceName("").ipv4(127 << 24 | 1).build());
  }

  @Test public void parseServerAddress_ipAndPortFromHost() {
    filter.parseServerAddress(HttpHost.create("http://127.0.0.1:9999"), span);

    verify(span).remoteEndpoint(
        Endpoint.builder().serviceName("").ipv4(127 << 24 | 1).port(9999).build());
  }

  @Test public void parseServerAddress_doesntNsLookup() {
    filter.parseServerAddress(HttpHost.create("http://localhost"), span);

    verifyNoMoreInteractions(span);
  }

  @Test public void parseServerAddress_doesntSkipWhenServiceNameIsNotBlank() {
    filter = new TracingHttpAsyncClientBuilder(
        HttpTracing.newBuilder(Tracing.newBuilder().build()).serverName("foo").build()
    );

    filter.parseServerAddress(HttpHost.create("http://localhost"), span);

    verify(span).remoteEndpoint(Endpoint.builder().serviceName("foo").build());
  }
}

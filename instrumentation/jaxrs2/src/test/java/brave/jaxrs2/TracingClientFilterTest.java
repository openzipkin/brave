package brave.jaxrs2;

import brave.Span;
import brave.Tracing;
import brave.http.HttpTracing;
import java.net.URI;
import javax.ws.rs.client.ClientRequestContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import zipkin.Endpoint;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TracingClientFilterTest {
  TracingClientFilter filter =
      new TracingClientFilter(HttpTracing.create(Tracing.newBuilder().build()));
  @Mock ClientRequestContext context;
  @Mock Span span;

  @Test public void parseServerAddress_skipsOnNoop() {
    when(span.isNoop()).thenReturn(true);
    filter.parseServerAddress(context, span);

    verify(span).isNoop();
    verifyNoMoreInteractions(context, span);
  }

  @Test public void parseServerAddress_ipFromHost() {
    when(span.isNoop()).thenReturn(false);
    when(context.getUri()).thenReturn(URI.create("http://127.0.0.1/foo"));
    filter.parseServerAddress(context, span);

    verify(span).remoteEndpoint(Endpoint.builder().serviceName("").ipv4(127 << 24 | 1).build());
  }

  @Test public void parseServerAddress_ipAndPortFromUri() {
    when(span.isNoop()).thenReturn(false);
    when(context.getUri()).thenReturn(URI.create("http://127.0.0.1:9999/foo"));
    filter.parseServerAddress(context, span);

    verify(span).remoteEndpoint(
        Endpoint.builder().serviceName("").ipv4(127 << 24 | 1).port(9999).build());
  }

  @Test public void parseServerAddress_doesntNsLookup() {
    when(span.isNoop()).thenReturn(false);
    when(context.getUri()).thenReturn(URI.create("http://localhost/foo"));
    filter.parseServerAddress(context, span);

    verify(span).isNoop();
    verifyNoMoreInteractions(span);
  }

  @Test public void parseServerAddress_doesntSkipWhenServiceNameIsNotBlank() {
    filter = new TracingClientFilter(
        HttpTracing.newBuilder(Tracing.newBuilder().build()).serverName("foo").build()
    );

    when(span.isNoop()).thenReturn(false);
    when(context.getUri()).thenReturn(URI.create("http://localhost/foo"));
    filter.parseServerAddress(context, span);

    verify(span).remoteEndpoint(Endpoint.builder().serviceName("foo").build());
  }
}

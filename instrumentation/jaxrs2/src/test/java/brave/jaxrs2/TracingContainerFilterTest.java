package brave.jaxrs2;

import brave.Span;
import javax.ws.rs.container.ContainerRequestContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import zipkin.Endpoint;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TracingContainerFilterTest {
  @Mock ContainerRequestContext context;
  @Mock Span span;

  @Test public void parseClientAddress_skipOnNoop() {
    when(span.isNoop()).thenReturn(true);
    TracingContainerFilter.parseClientAddress(context, span);

    verify(span).isNoop();
    verifyNoMoreInteractions(context, span);
  }

  @Test public void parseClientAddress_ipFromXForwardedFor() {
    when(span.isNoop()).thenReturn(false);
    when(context.getHeaderString("X-Forwarded-For")).thenReturn("127.0.0.1");
    TracingContainerFilter.parseClientAddress(context, span);

    verify(span).remoteEndpoint(Endpoint.builder().serviceName("").ipv4(127 << 24 | 1).build());
  }

  @Test public void parseClientAddress_skipsOnNoIp() {
    when(span.isNoop()).thenReturn(false);
    TracingContainerFilter.parseClientAddress(context, span);

    verify(span).isNoop();
    verifyNoMoreInteractions(span);
  }

  @Test public void parseClientAddress_doesntNsLookup() {
    when(span.isNoop()).thenReturn(false);
    when(context.getHeaderString("X-Forwarded-For")).thenReturn("localhost");
    TracingContainerFilter.parseClientAddress(context, span);

    verify(span).isNoop();
    verifyNoMoreInteractions(span);
  }
}

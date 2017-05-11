package brave.okhttp3;

import brave.Span;
import brave.Tracing;
import brave.http.HttpTracing;
import okhttp3.Connection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TracingInterceptorTest {
  TracingInterceptor filter =
      new TracingInterceptor(HttpTracing.create(Tracing.newBuilder().build()));
  @Mock Connection connection;
  @Mock Span span;

  @Test public void parseServerAddress_skipsOnNoop() {
    when(span.isNoop()).thenReturn(true);
    filter.parseServerAddress(connection, span);

    verify(span).isNoop();
    verifyNoMoreInteractions(span);
  }
}

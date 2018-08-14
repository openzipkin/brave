package brave.okhttp3;

import brave.Span;
import brave.Tracing;
import brave.propagation.ThreadLocalCurrentTraceContext;
import okhttp3.Interceptor;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import zipkin2.reporter.Reporter;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TracingInterceptorTest {
  Tracing tracing = Tracing.newBuilder()
      .currentTraceContext(ThreadLocalCurrentTraceContext.create())
      .spanReporter(Reporter.NOOP)
      .build();
  @Mock Interceptor.Chain chain;
  @Mock Span span;

  @Test public void parseRouteAddress_skipsOnNoop() {
    when(span.isNoop()).thenReturn(true);
    TracingInterceptor.parseRouteAddress(chain, span);

    verify(span).isNoop();
    verifyNoMoreInteractions(span);
  }

  @After public void close() {
    tracing.close();
  }
}

package brave.test.http;

import brave.ScopedSpan;
import brave.Tracer;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Test;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class ITHttpAsyncClient<C> extends ITHttpClient<C> {

  protected abstract void getAsync(C client, String pathIncludingQuery) throws Exception;

  /**
   * This tests that the parent is determined at the time the request was made, not when the request
   * was executed.
   */
  @Test public void usesParentFromInvocationTime() throws Exception {
    Tracer tracer = httpTracing.tracing().tracer();
    server.enqueue(new MockResponse().setBodyDelay(300, TimeUnit.MILLISECONDS));
    server.enqueue(new MockResponse());

    ScopedSpan parent = tracer.startScopedSpan("test");
    try {
      getAsync(client, "/items/1");
      getAsync(client, "/items/2");
    } finally {
      parent.finish();
    }

    ScopedSpan otherSpan = tracer.startScopedSpan("test2");
    try {
      for (int i = 0; i < 2; i++) {
        RecordedRequest request = server.takeRequest();
        assertThat(request.getHeader("x-b3-traceId"))
            .isEqualTo(parent.context().traceIdString());
        assertThat(request.getHeader("x-b3-parentspanid"))
            .isEqualTo(parent.context().spanIdString());
      }
    } finally {
      otherSpan.finish();
    }

    // Check we reported 2 in-process spans and 2 RPC client spans
    assertThat(Arrays.asList(takeSpan(), takeSpan(), takeSpan(), takeSpan()))
        .extracting(Span::kind)
        .containsOnly(null, Span.Kind.CLIENT);
  }
}

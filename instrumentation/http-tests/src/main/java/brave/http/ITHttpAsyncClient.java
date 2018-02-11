package brave.http;

import brave.Tracer;
import brave.Tracer.SpanInScope;
import brave.internal.HexCodec;
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

    brave.Span parent = tracer.newTrace().name("test").start();
    try (SpanInScope ws = tracer.withSpanInScope(parent)) {
      getAsync(client, "/items/1");
      getAsync(client, "/items/2");
    } finally {
      parent.finish();
    }

    brave.Span otherSpan = tracer.newTrace().name("test2").start();
    try (SpanInScope ws = tracer.withSpanInScope(otherSpan)) {
      for (int i = 0; i < 2; i++) {
        RecordedRequest request = server.takeRequest();
        assertThat(request.getHeader("x-b3-traceId"))
            .isEqualTo(parent.context().traceIdString());
        assertThat(request.getHeader("x-b3-parentspanid"))
            .isEqualTo(HexCodec.toLowerHex(parent.context().spanId()));
      }
    } finally {
      otherSpan.finish();
    }

    // Check we reported 2 local spans and 2 client spans
    assertThat(Arrays.asList(takeSpan(), takeSpan(), takeSpan(), takeSpan()))
        .extracting(Span::kind)
        .containsOnly(null, Span.Kind.CLIENT);
  }
}

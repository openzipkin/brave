package brave.interop;

import brave.Span;
import brave.Tracing;
import brave.propagation.TraceContextOrSamplingFlags;
import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.TracerAdapter;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import zipkin2.Endpoint;
import zipkin2.storage.InMemoryStorage;

import static brave.internal.HexCodec.toLowerHex;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * This is an example of interop between Brave 3 and Brave 4.
 *
 * <p>This creates a..
 * <ol>
 * <li>root server span with Brave 3</li>
 * <li>one-way child span with Brave 4</li>
 * <li>local grandchild span with Brave 3</li>
 * </ol>
 *
 * <p>The key lesson here is that Brave 3 works via thread locals. Via {@link TracerAdapter}, you
 * can set or get the current span used in Brave 3.
 */
public class MixedBraveVersionsExample {
  @Rule public MockWebServer server = new MockWebServer();

  InMemoryStorage storage = InMemoryStorage.newBuilder().build();

  /** Use different tracers for client and server as usually they are on different hosts. */
  Tracing brave4Client = Tracing.newBuilder()
      .localEndpoint(Endpoint.newBuilder().serviceName("client").build())
      .spanReporter(s -> storage.spanConsumer().accept(Collections.singletonList(s)))
      .build();
  Brave brave3Client = TracerAdapter.newBrave(brave4Client.tracer());
  Tracing brave4Server = Tracing.newBuilder()
      .localEndpoint(Endpoint.newBuilder().serviceName("server").build())
      .spanReporter(s -> storage.spanConsumer().accept(Collections.singletonList(s)))
      .build();
  Brave brave3Server = TracerAdapter.newBrave(brave4Server.tracer());

  CountDownLatch flushedIncomingRequest = new CountDownLatch(1);

  @Before public void setup() {
    server.setDispatcher(new Dispatcher() {
      @Override public MockResponse dispatch(RecordedRequest recordedRequest) {
        Span finishedOneWaySpan = joinOneWaySpan(recordedRequest);
        attachParentToCurrentThread(finishedOneWaySpan);

        // Create an example span using brave 3. The parent should be the one-way span
        brave3Server.localTracer().startNewSpan("message-processor", "process");
        brave3Server.localTracer().finishSpan();

        flushedIncomingRequest.countDown();
        // eventhough the client doesn't read the response, we return one
        return new MockResponse();
      }
    });
  }

  @After public void close(){
    Tracing.current().close();
  }

  @Test
  public void createTraceWithBrave3AndBrave4() throws Exception {
    brave3Client.serverTracer().setStateUnknown("get");
    brave3Client.serverTracer().setServerReceived();
    Span parent = getServerSpanFromBrave3();
    createAndPropagateOneWaySpan(parent);
    brave3Client.serverTracer().setServerSend(); // close the parent span

    // block on the server handling the request, so we can run assertions
    flushedIncomingRequest.await();

    // And now we have a..
    // * root server span created with Brave 3
    // * one-way child span created with Brave 4
    // * local grandchild span created with Brave 3
    List<zipkin2.Span>
        trace = storage.spanStore().getTrace(toLowerHex(parent.context().traceId())).execute();
    assertThat(trace).hasSize(4);
    assertThat(trace.get(2).id()).isEqualTo(trace.get(0).parentId());
    assertThat(trace.get(0).id()).isEqualTo(trace.get(3).parentId());
  }

  /**
   * Let's pretend we had an existing trace created by a brave 3 server tracer. In order to use
   * Brave 4 in that existing trace, you need to get a reference to the current span.
   */
  Span getServerSpanFromBrave3() {
    return TracerAdapter.getServerSpan(brave4Client.tracer(),
        brave3Client.serverSpanThreadBinder());
  }

  /**
   * This shows how to create a one-way child span using Brave 4. The notable part here is that it
   * annotates "cs" and flushes the span.
   */
  void createAndPropagateOneWaySpan(Span parent) {
    // start a new span representing a request
    Span span = brave4Client.tracer().newChild(parent.context());

    // inject the trace context into the request
    Request.Builder request = new Request.Builder().url(server.url("/"));
    brave4Client.propagation().injector(Request.Builder::addHeader).inject(span.context(), request);

    // fire off the request asynchronously, totally dropping any response
    new OkHttpClient().newCall(request.build()).enqueue(mock(Callback.class));
    span.kind(Span.Kind.CLIENT).flush(); // record the timestamp of the client send and flush
  }

  /**
   * This shows how to join a one-way span using Brave 4. The notable part here is that it annotates
   * "sr" and flushes the span. Also notice we return the completed span (to create children).
   */
  Span joinOneWaySpan(RecordedRequest recordedRequest) {
    TraceContextOrSamplingFlags extracted =
        brave4Server.propagation().extractor(RecordedRequest::getHeader).extract(recordedRequest);

    Span serverSpan = extracted.context() != null
        ? brave4Server.tracer().joinSpan(extracted.context())
        : brave4Server.tracer().nextSpan(extracted);

    serverSpan.name(recordedRequest.getMethod())
        .kind(Span.Kind.SERVER)
        .flush(); // record the timestamp of the server receive and flush
    return serverSpan;
  }

  /**
   * In order to join traces with Brave 3 tracers, you need to attach a parent span to the the
   * current thread. This shows how to attach a Brave 4 span as a parent.
   */
  void attachParentToCurrentThread(Span parent) {
    TracerAdapter.setServerSpan(parent.context(), brave3Server.serverSpanThreadBinder());
  }
}

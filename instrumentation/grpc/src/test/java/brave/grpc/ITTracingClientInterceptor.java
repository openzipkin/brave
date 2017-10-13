package brave.grpc;

import brave.SpanCustomizer;
import brave.Tracer;
import brave.Tracing;
import brave.context.log4j2.ThreadContextCurrentTraceContext;
import brave.propagation.StrictCurrentTraceContext;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.sampler.Sampler;
import com.google.common.util.concurrent.ListenableFuture;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.StatusRuntimeException;
import io.grpc.examples.helloworld.GraterGrpc;
import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import zipkin2.Span;

import static brave.grpc.GreeterImpl.HELLO_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.junit.Assume.assumeTrue;

public class ITTracingClientInterceptor {
  Logger testLogger = LogManager.getLogger();

  ConcurrentLinkedDeque<Span> spans = new ConcurrentLinkedDeque<>();

  GrpcTracing tracing = GrpcTracing.create(tracingBuilder(Sampler.ALWAYS_SAMPLE).build());
  Tracer tracer = tracing.tracing().tracer();
  TestServer server = new TestServer();
  ManagedChannel client;

  @Before public void setup() throws IOException {
    server.start();
    client = newClient();
  }

  @After public void close() throws Exception {
    closeClient(client);
    server.stop();
    Tracing current = Tracing.current();
    if (current != null) current.close();
  }

  ManagedChannel newClient() {
    return newClient(tracing.newClientInterceptor());
  }

  ManagedChannel newClient(ClientInterceptor... clientInterceptors) {
    return ManagedChannelBuilder.forAddress("localhost", server.port())
        .intercept(clientInterceptors)
        .usePlaintext(true)
        .build();
  }

  void closeClient(ManagedChannel client) throws Exception {
    client.shutdown();
    client.awaitTermination(1, TimeUnit.SECONDS);
  }

  @Test public void propagatesSpan() throws Exception {
    GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);

    TraceContext context = server.takeRequest().context();
    assertThat(context.parentId()).isNull();
    assertThat(context.sampled()).isTrue();
  }

  @Test public void makesChildOfCurrentSpan() throws Exception {
    brave.Span parent = tracer.newTrace().name("test").start();
    try (Tracer.SpanInScope ws = tracer.withSpanInScope(parent)) {
      GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);
    } finally {
      parent.finish();
    }

    TraceContext context = server.takeRequest().context();
    assertThat(context.traceId())
        .isEqualTo(parent.context().traceId());
    assertThat(context.parentId())
        .isEqualTo(parent.context().spanId());
  }

  /**
   * This tests that the parent is determined at the time the request was made, not when the request
   * was executed.
   */
  @Test public void usesParentFromInvocationTime() throws Exception {
    server.enqueueDelay(TimeUnit.SECONDS.toMillis(1));
    GreeterGrpc.GreeterFutureStub futureStub = GreeterGrpc.newFutureStub(client);

    brave.Span parent = tracer.newTrace().name("test").start();
    try (Tracer.SpanInScope ws = tracer.withSpanInScope(parent)) {
      futureStub.sayHello(HELLO_REQUEST);
      futureStub.sayHello(HELLO_REQUEST);
    } finally {
      parent.finish();
    }

    brave.Span otherSpan = tracer.newTrace().name("test2").start();
    try (Tracer.SpanInScope ws = tracer.withSpanInScope(otherSpan)) {
      for (int i = 0; i < 2; i++) {
        TraceContext context = server.takeRequest().context();
        assertThat(context.traceId())
            .isEqualTo(parent.context().traceId());
        assertThat(context.parentId())
            .isEqualTo(parent.context().spanId());
      }
    } finally {
      otherSpan.finish();
    }
  }

  /** Unlike Brave 3, Brave 4 propagates trace ids even when unsampled */
  @Test public void propagates_sampledFalse() throws Exception {
    tracing = GrpcTracing.create(tracingBuilder(Sampler.NEVER_SAMPLE).build());
    closeClient(client);
    client = newClient();

    GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);

    TraceContextOrSamplingFlags extracted = server.takeRequest();
    assertThat(extracted.sampled()).isFalse();
  }

  @Test public void reportsClientKindToZipkin() throws Exception {
    GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);

    assertThat(spans)
        .extracting(Span::kind)
        .containsExactly(Span.Kind.CLIENT);
  }

  @Test public void defaultSpanNameIsMethodName() throws Exception {
    GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);

    assertThat(spans)
        .extracting(Span::name)
        .containsExactly("helloworld.greeter/sayhello");
  }

  @Test public void reportsSpanOnTransportException() throws Exception {
    server.stop();

    try {
      GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);
      failBecauseExceptionWasNotThrown(StatusRuntimeException.class);
    } catch (StatusRuntimeException e) {
    }

    assertThat(spans).flatExtracting(s -> s.tags().entrySet()).containsExactly(
        entry("error", "UNAVAILABLE"),
        entry("grpc.status_code", "UNAVAILABLE")
    );
  }

  @Test public void addsErrorTag_onUnimplemented() throws Exception {
    try {
      GraterGrpc.newBlockingStub(client).seyHallo(HELLO_REQUEST);
      failBecauseExceptionWasNotThrown(StatusRuntimeException.class);
    } catch (StatusRuntimeException e) {
    }

    assertThat(spans).flatExtracting(s -> s.tags().entrySet()).containsExactly(
        entry("error", "UNIMPLEMENTED"),
        entry("grpc.status_code", "UNIMPLEMENTED")
    );
  }

  @Test public void addsErrorTag_onTransportException() throws Exception {
    reportsSpanOnTransportException();

    assertThat(spans).flatExtracting(s -> s.tags().entrySet()).containsExactly(
        entry("error", "UNAVAILABLE"),
        entry("grpc.status_code", "UNAVAILABLE")
    );
  }

  @Test public void addsErrorTag_onCanceledFuture() throws Exception {
    server.enqueueDelay(TimeUnit.SECONDS.toMillis(1));

    ListenableFuture<HelloReply> resp = GreeterGrpc.newFutureStub(client).sayHello(HELLO_REQUEST);
    assumeTrue("lost race on cancel", resp.cancel(true));

    close(); // blocks until the cancel finished

    assertThat(spans).flatExtracting(s -> s.tags().entrySet()).containsExactly(
        entry("error", "CANCELLED"),
        entry("grpc.status_code", "CANCELLED")
    );
  }

  /**
   * NOTE: for this to work, the tracing interceptor must be last (so that it executes first)
   *
   * <p>Also notice that we are only making the current context available in the request side.
   */
  @Test public void currentSpanVisibleToUserInterceptors() throws Exception {
    Map<String, String> scopes = new ConcurrentHashMap<>();
    closeClient(client);

    client = newClient(
        new ClientInterceptor() {
          @Override public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
              MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
            testLogger.info("in span!");
            scopes.put("before", tracer.currentSpan().context().traceIdString());
            return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
                next.newCall(method, callOptions)) {
              @Override
              public void start(Listener<RespT> responseListener, Metadata headers) {
                scopes.put("start", tracer.currentSpan().context().traceIdString());
                super.start(responseListener, headers);
              }
            };
          }
        },
        tracing.newClientInterceptor()
    );

    GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);

    assertThat(scopes)
        .containsKeys("before", "start");
  }

  @Test public void clientParserTest() throws Exception {
    closeClient(client);
    tracing = tracing.toBuilder().clientParser(new GrpcClientParser() {
      @Override protected <M> void onMessageSent(M message, SpanCustomizer span) {
        span.tag("grpc.message_sent", message.toString());
      }

      @Override protected <M> void onMessageReceived(M message, SpanCustomizer span) {
        span.tag("grpc.message_received", message.toString());
      }

      @Override
      protected <ReqT, RespT> String spanName(MethodDescriptor<ReqT, RespT> methodDescriptor) {
        return methodDescriptor.getType().name();
      }
    }).build();
    client = newClient();

    GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);

    assertThat(spans.getFirst().name()).isEqualTo("unary");
    assertThat(spans).flatExtracting(s -> s.tags().keySet()).containsExactlyInAnyOrder(
        "grpc.message_received", "grpc.message_sent"
    );
  }

  @Test
  public void clientParserTestStreamingResponse() throws Exception {
    closeClient(client);
    tracing = tracing.toBuilder().clientParser(new GrpcClientParser() {
      int receiveCount = 0;

      @Override protected <M> void onMessageReceived(M message, SpanCustomizer span) {
        span.tag("grpc.message_received." + receiveCount++, message.toString());
      }
    }).build();
    client = newClient();

    Iterator<HelloReply> replies = GreeterGrpc.newBlockingStub(client)
        .sayHelloWithManyReplies(HelloRequest.newBuilder().setName("this is dog").build());
    assertThat(replies).hasSize(10);
    assertThat(spans).hasSize(1);
    // all response messages are tagged to the same span
    assertThat(spans.getFirst().tags()).hasSize(10);
  }

  Tracing.Builder tracingBuilder(Sampler sampler) {
    return Tracing.newBuilder()
        .spanReporter((zipkin2.reporter.Reporter<zipkin2.Span>) spans::add)
        .currentTraceContext( // connect to log4j
            ThreadContextCurrentTraceContext.create(new StrictCurrentTraceContext()))
        .sampler(sampler);
  }
}

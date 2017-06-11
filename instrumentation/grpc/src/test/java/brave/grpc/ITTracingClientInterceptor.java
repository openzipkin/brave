package brave.grpc;

import brave.Tracer;
import brave.Tracing;
import brave.context.log4j2.ThreadContextCurrentTraceContext;
import brave.internal.StrictCurrentTraceContext;
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
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import zipkin.Constants;
import zipkin.Span;
import zipkin.internal.Util;

import static brave.grpc.GreeterImpl.HELLO_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.junit.Assume.assumeTrue;

public class ITTracingClientInterceptor {
  Logger testLogger = LogManager.getLogger();

  ConcurrentLinkedDeque<Span> spans = new ConcurrentLinkedDeque<>();

  Tracing tracing;
  TestServer server = new TestServer();
  ManagedChannel client;

  @Before public void setup() throws IOException {
    server.start();
    tracing = tracingBuilder(Sampler.ALWAYS_SAMPLE).build();
    client = newClient();
  }

  @After public void close() throws Exception {
    closeClient(client);
    server.stop();
  }

  ManagedChannel newClient() {
    return newClient(GrpcTracing.create(tracing).newClientInterceptor());
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
    brave.Span parent = tracing.tracer().newTrace().name("test").start();
    try (Tracer.SpanInScope ws = tracing.tracer().withSpanInScope(parent)) {
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

    brave.Span parent = tracing.tracer().newTrace().name("test").start();
    try (Tracer.SpanInScope ws = tracing.tracer().withSpanInScope(parent)) {
      futureStub.sayHello(HELLO_REQUEST);
      futureStub.sayHello(HELLO_REQUEST);
    } finally {
      parent.finish();
    }

    brave.Span otherSpan = tracing.tracer().newTrace().name("test2").start();
    try (Tracer.SpanInScope ws = tracing.tracer().withSpanInScope(otherSpan)) {
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
    tracing = tracingBuilder(Sampler.NEVER_SAMPLE).build();
    closeClient(client);
    client = newClient();

    GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);

    TraceContextOrSamplingFlags context = server.takeRequest();
    assertThat(context.context().sampled()).isFalse();
  }

  @Test public void reportsClientAnnotationsToZipkin() throws Exception {
    GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);

    assertThat(spans)
        .flatExtracting(s -> s.annotations)
        .extracting(a -> a.value)
        .containsExactly("cs", "cr");
  }

  @Test public void defaultSpanNameIsMethodName() throws Exception {
    GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);

    assertThat(spans)
        .extracting(s -> s.name)
        .containsExactly("helloworld.greeter/sayhello");
  }

  @Test public void reportsSpanOnTransportException() throws Exception {
    server.stop();

    try {
      GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);
      failBecauseExceptionWasNotThrown(StatusRuntimeException.class);
    } catch (StatusRuntimeException e) {
    }

    assertThat(spans).hasSize(1);
  }

  @Test public void addsErrorTag_onUnimplemented() throws Exception {

    try {
      GraterGrpc.newBlockingStub(client).seyHallo(HELLO_REQUEST);
      failBecauseExceptionWasNotThrown(StatusRuntimeException.class);
    } catch (StatusRuntimeException e) {
    }

    assertThat(spans)
        .flatExtracting(s -> s.binaryAnnotations)
        .filteredOn(a -> a.key.equals(Constants.ERROR))
        .extracting(a -> new String(a.value, Util.UTF_8))
        .containsExactly("UNIMPLEMENTED");
  }

  @Test public void addsErrorTag_onTransportException() throws Exception {
    reportsSpanOnTransportException();

    assertThat(spans)
        .flatExtracting(s -> s.binaryAnnotations)
        .filteredOn(a -> a.key.equals(Constants.ERROR))
        .extracting(a -> new String(a.value, Util.UTF_8))
        .containsExactly("UNAVAILABLE");
  }

  @Test public void addsErrorTag_onCanceledFuture() throws Exception {
    server.enqueueDelay(TimeUnit.SECONDS.toMillis(1));

    ListenableFuture<HelloReply> resp = GreeterGrpc.newFutureStub(client).sayHello(HELLO_REQUEST);
    assumeTrue("lost race on cancel", resp.cancel(true));

    close(); // blocks until the cancel finished

    assertThat(spans)
        .flatExtracting(s -> s.binaryAnnotations)
        .filteredOn(a -> a.key.equals(Constants.ERROR))
        .extracting(a -> new String(a.value, Util.UTF_8))
        .containsExactly("CANCELLED");
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
            scopes.put("before", tracing.currentTraceContext().get().traceIdString());
            return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
                next.newCall(method, callOptions)) {
              @Override
              public void start(Listener<RespT> responseListener, Metadata headers) {
                scopes.put("start", tracing.currentTraceContext().get().traceIdString());
                super.start(responseListener, headers);
              }
            };
          }
        },
        GrpcTracing.create(tracing).newClientInterceptor()
    );

    GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);

    assertThat(scopes)
        .containsKeys("before", "start");
  }

  Tracing.Builder tracingBuilder(Sampler sampler) {
    return Tracing.newBuilder()
        .reporter(spans::add)
        .currentTraceContext( // connect to log4
            ThreadContextCurrentTraceContext.create(new StrictCurrentTraceContext()))
        .sampler(sampler);
  }
}

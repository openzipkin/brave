/*
 * Copyright 2013-2020 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.grpc;

import brave.Clock;
import brave.SpanCustomizer;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.ExtraFieldPropagation;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import brave.rpc.RpcRuleSampler;
import brave.rpc.RpcTracing;
import brave.test.ITRemote;
import brave.test.util.AssertableCallback;
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
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import zipkin2.Annotation;
import zipkin2.Span;

import static brave.grpc.GreeterImpl.HELLO_REQUEST;
import static brave.rpc.RpcRequestMatchers.methodEquals;
import static brave.rpc.RpcRequestMatchers.serviceEquals;
import static brave.sampler.Sampler.ALWAYS_SAMPLE;
import static brave.sampler.Sampler.NEVER_SAMPLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.junit.Assume.assumeTrue;

public abstract class BaseITTracingClientInterceptor extends ITRemote {
  TestServer server = new TestServer(propagationFactory);
  ManagedChannel client;
  GrpcTracing grpcTracing = GrpcTracing.create(tracing);

  @Before public void setup() throws IOException {
    server.start();
    client = newClient();
  }

  @After public void close() throws Exception {
    closeClient(client);
    server.stop();
  }

  ManagedChannel newClient() {
    return newClient(grpcTracing.newClientInterceptor());
  }

  ManagedChannel newClient(ClientInterceptor... clientInterceptors) {
    return usePlainText(ManagedChannelBuilder.forAddress("localhost", server.port())
      .intercept(clientInterceptors)).build();
  }

  /** Extracted as {@link ManagedChannelBuilder#usePlaintext()} is a version-specific signature */
  protected abstract ManagedChannelBuilder<?> usePlainText(ManagedChannelBuilder<?> localhost);

  void closeClient(ManagedChannel client) throws Exception {
    client.shutdown();
    client.awaitTermination(1, TimeUnit.SECONDS);
  }

  @Test public void propagatesNewTrace() throws Exception {
    GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);

    TraceContext extracted = server.takeRequest().context();
    assertThat(extracted.sampled()).isTrue();
    assertThat(extracted.parentIdString()).isNull();
    assertSameIds(takeRemoteSpan(Span.Kind.CLIENT), extracted);
  }

  @Test public void propagatesChildOfCurrentSpan() throws Exception {
    TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);
    try (Scope scope = currentTraceContext.newScope(parent)) {
      GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);
    }

    TraceContext extracted = server.takeRequest().context();
    assertThat(extracted.sampled()).isTrue();
    assertChildOf(extracted, parent);
    assertSameIds(takeRemoteSpan(Span.Kind.CLIENT), extracted);
  }

  /** Unlike Brave 3, Brave 4 propagates trace ids even when unsampled */
  @Test public void propagatesUnsampledContext() throws Exception {
    TraceContext parent = newTraceContext(SamplingFlags.NOT_SAMPLED);
    try (Scope scope = currentTraceContext.newScope(parent)) {
      GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);
    }

    TraceContext extracted = server.takeRequest().context();
    assertThat(extracted.sampled()).isFalse();
    assertChildOf(extracted, parent);
  }

  @Test public void propagatesExtra() throws Exception {
    TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);
    try (Scope scope = currentTraceContext.newScope(parent)) {
      ExtraFieldPropagation.set(parent, EXTRA_KEY, "joey");
      GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);
    }

    TraceContext extracted = server.takeRequest().context();
    assertThat(ExtraFieldPropagation.get(extracted, EXTRA_KEY)).isEqualTo("joey");

    takeRemoteSpan(Span.Kind.CLIENT);
  }

  @Test public void propagatesExtra_unsampled() throws Exception {
    TraceContext parent = newTraceContext(SamplingFlags.NOT_SAMPLED);
    try (Scope scope = currentTraceContext.newScope(parent)) {
      ExtraFieldPropagation.set(parent, EXTRA_KEY, "joey");
      GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);
    }

    TraceContext extracted = server.takeRequest().context();
    assertThat(ExtraFieldPropagation.get(extracted, EXTRA_KEY)).isEqualTo("joey");
  }

  /** This prevents confusion as a blocking client should end before, the start of the next span. */
  @Test public void clientTimestampAndDurationEnclosedByParent() throws Exception {
    TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);
    Clock clock = tracing.clock(parent);

    long start = clock.currentTimeMicroseconds();
    try (Scope scope = currentTraceContext.newScope(parent)) {
      GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);
    }
    long finish = clock.currentTimeMicroseconds();

    Span clientSpan = takeRemoteSpan(Span.Kind.CLIENT);
    assertChildOf(clientSpan, parent);
    assertSpanInInterval(clientSpan, start, finish);
  }

  /**
   * This tests that the parent is determined at the time the request was made, not when the request
   * was executed.
   */
  @Test public void usesParentFromInvocationTime() throws Exception {
    server.enqueueDelay(TimeUnit.SECONDS.toMillis(1));
    GreeterGrpc.GreeterFutureStub futureStub = GreeterGrpc.newFutureStub(client);

    TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);
    try (Scope scope = currentTraceContext.newScope(parent)) {
      futureStub.sayHello(HELLO_REQUEST);
      futureStub.sayHello(HELLO_REQUEST);
    }

    try (Scope scope = currentTraceContext.newScope(null)) {
      for (int i = 0; i < 2; i++) {
        TraceContext extracted = server.takeRequest().context();
        assertChildOf(extracted, parent);
      }
    }

    // The spans may report in a different order than the requests
    for (int i = 0; i < 2; i++) {
      assertChildOf(takeRemoteSpan(Span.Kind.CLIENT), parent);
    }
  }

  @Test public void customSampler() throws Exception {
    closeClient(client);

    RpcTracing rpcTracing = RpcTracing.newBuilder(tracing).clientSampler(RpcRuleSampler.newBuilder()
      .putRule(methodEquals("SayHelloWithManyReplies"), NEVER_SAMPLE)
      .putRule(serviceEquals("helloworld.greeter"), ALWAYS_SAMPLE)
      .build()).build();

    grpcTracing = GrpcTracing.create(rpcTracing);
    client = newClient();

    // unsampled
    // NOTE: An iterator request is lazy: invoking the iterator invokes the request
    GreeterGrpc.newBlockingStub(client).sayHelloWithManyReplies(HELLO_REQUEST).hasNext();

    // sampled
    GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);

    assertThat(takeRemoteSpan(Span.Kind.CLIENT).name())
      .isEqualTo("helloworld.greeter/sayhello");
    // @After will also check that sayHelloWithManyReplies was not sampled
  }

  @Test public void reportsClientKindToZipkin() throws Exception {
    GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);

    takeRemoteSpan(Span.Kind.CLIENT);
  }

  @Test public void defaultSpanNameIsMethodName() throws Exception {
    GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);

    assertThat(takeRemoteSpan(Span.Kind.CLIENT).name())
      .isEqualTo("helloworld.greeter/sayhello");
  }

  @Test public void onTransportException_addsErrorTag() throws Exception {
    server.stop();

    StatusRuntimeException thrown = catchThrowableOfType(
      () -> GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST),
      StatusRuntimeException.class);

    Span span = takeRemoteSpanWithError(Span.Kind.CLIENT, thrown.getStatus().getCode().toString());
    assertThat(span.tags().get("grpc.status_code"))
      .isEqualTo(span.tags().get("error"));
  }

  @Test public void addsErrorTag_onUnimplemented() throws Exception {
    try {
      GraterGrpc.newBlockingStub(client).seyHallo(HELLO_REQUEST);
      failBecauseExceptionWasNotThrown(StatusRuntimeException.class);
    } catch (StatusRuntimeException e) {
    }

    Span span = takeRemoteSpanWithError(Span.Kind.CLIENT, "UNIMPLEMENTED");
    assertThat(span.tags().get("grpc.status_code"))
      .isEqualTo(span.tags().get("error"));
  }

  @Test public void addsErrorTag_onCanceledFuture() throws Exception {
    server.enqueueDelay(TimeUnit.SECONDS.toMillis(1));

    ListenableFuture<HelloReply> resp = GreeterGrpc.newFutureStub(client).sayHello(HELLO_REQUEST);
    assumeTrue("lost race on cancel", resp.cancel(true));

    Span span = takeRemoteSpanWithError(Span.Kind.CLIENT, "CANCELLED");
    assertThat(span.tags().get("grpc.status_code"))
      .isEqualTo(span.tags().get("error"));
  }

  /**
   * NOTE: for this to work, the tracing interceptor must be last (so that it executes first)
   *
   * <p>Also notice that we are only making the current context available in the request side.
   */
  @Test public void currentSpanVisibleToUserInterceptors() throws Exception {
    closeClient(client);

    client = newClient(
      new ClientInterceptor() {
        @Override public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
          MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
          tracing.tracer().currentSpanCustomizer().annotate("before");
          return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
            next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
              tracing.tracer().currentSpanCustomizer().annotate("start");
              super.start(responseListener, headers);
            }
          };
        }
      },
      grpcTracing.newClientInterceptor()
    );

    GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);

    assertThat(takeRemoteSpan(Span.Kind.CLIENT).annotations())
      .extracting(Annotation::value)
      .containsOnly("before", "start");
  }

  @Test public void clientParserTest() throws Exception {
    closeClient(client);
    grpcTracing = grpcTracing.toBuilder().clientParser(new GrpcClientParser() {
      @Override protected <M> void onMessageSent(M message, SpanCustomizer span) {
        span.tag("grpc.message_sent", message.toString());
        if (tracing.currentTraceContext().get() != null) {
          span.tag("grpc.message_sent.visible", "true");
        }
      }

      @Override protected <M> void onMessageReceived(M message, SpanCustomizer span) {
        span.tag("grpc.message_received", message.toString());
        if (tracing.currentTraceContext().get() != null) {
          span.tag("grpc.message_received.visible", "true");
        }
      }

      @Override
      protected <ReqT, RespT> String spanName(MethodDescriptor<ReqT, RespT> methodDescriptor) {
        return methodDescriptor.getType().name();
      }
    }).build();
    client = newClient();

    GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);

    Span span = takeRemoteSpan(Span.Kind.CLIENT);
    assertThat(span.name()).isEqualTo("unary");
    assertThat(span.tags()).containsKeys(
      "grpc.message_received", "grpc.message_sent",
      "grpc.message_received.visible", "grpc.message_sent.visible"
    );
  }

  @Test public void clientParserTestStreamingResponse() throws Exception {
    closeClient(client);
    grpcTracing = grpcTracing.toBuilder().clientParser(new GrpcClientParser() {
      int receiveCount = 0;

      @Override protected <M> void onMessageReceived(M message, SpanCustomizer span) {
        span.tag("grpc.message_received." + receiveCount++, message.toString());
      }
    }).build();
    client = newClient();

    Iterator<HelloReply> replies = GreeterGrpc.newBlockingStub(client)
      .sayHelloWithManyReplies(HelloRequest.newBuilder().setName("this is dog").build());
    assertThat(replies).toIterable().hasSize(10);

    // all response messages are tagged to the same span
    assertThat(takeRemoteSpan(Span.Kind.CLIENT).tags()).hasSize(10);
  }

  /**
   * This ensures that response callbacks run in the invocation context, not the client one. This
   * allows async chaining to appear caused by the parent, not by the most recent client. Otherwise,
   * we would see a client span child of a client span, which could be confused with duplicate
   * instrumentation and affect dependency link counts.
   */
  @Test public void callbackContextIsFromInvocationTime() throws Exception {
    AssertableCallback<HelloReply> callback = new AssertableCallback<>();

    // Capture the current trace context when onSuccess or onError occur
    AtomicReference<TraceContext> invocationContext = new AtomicReference<>();
    callback.setListener(() -> invocationContext.set(currentTraceContext.get()));

    TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);
    try (Scope scope = currentTraceContext.newScope(parent)) {
      GreeterGrpc.newStub(client).sayHello(HELLO_REQUEST, new StreamObserverAdapter(callback));
    }

    callback.assertThatSuccess().isNotNull(); // ensures listener ran
    assertThat(invocationContext.get()).isSameAs(parent);
    assertChildOf(takeRemoteSpan(Span.Kind.CLIENT), parent);
  }

  /** This ensures that response callbacks run when there is no invocation trace context. */
  @Test public void callbackContextIsFromInvocationTime_root() throws Exception {
    AssertableCallback<HelloReply> callback = new AssertableCallback<>();

    // Capture the current trace context when onSuccess or onError occur
    AtomicReference<TraceContext> invocationContext = new AtomicReference<>();
    callback.setListener(() -> invocationContext.set(currentTraceContext.get()));

    GreeterGrpc.newStub(client).sayHello(HELLO_REQUEST, new StreamObserverAdapter(callback));

    callback.assertThatSuccess().isNotNull(); // ensures listener ran
    assertThat(invocationContext.get()).isNull();
    assertThat(takeRemoteSpan(Span.Kind.CLIENT).parentId()).isNull();
  }

  static final class StreamObserverAdapter implements StreamObserver<HelloReply> {
    final AssertableCallback<HelloReply> callback;

    StreamObserverAdapter(AssertableCallback<HelloReply> callback) {
      this.callback = callback;
    }

    @Override public void onNext(HelloReply helloReply) {
      callback.onSuccess(helloReply);
    }

    @Override public void onError(Throwable throwable) {
      callback.onError(throwable);
    }

    @Override public void onCompleted() {
    }
  }
}

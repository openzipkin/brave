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

import brave.CurrentSpanCustomizer;
import brave.SpanCustomizer;
import brave.internal.Nullable;
import brave.propagation.B3SingleFormat;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import brave.rpc.RpcRuleSampler;
import brave.rpc.RpcTracing;
import brave.test.ITRemote;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.ForwardingServerCall.SimpleForwardingServerCall;
import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import zipkin2.Span;

import static brave.grpc.GreeterImpl.HELLO_REQUEST;
import static brave.rpc.RpcRequestMatchers.methodEquals;
import static brave.rpc.RpcRequestMatchers.serviceEquals;
import static brave.sampler.Sampler.ALWAYS_SAMPLE;
import static brave.sampler.Sampler.NEVER_SAMPLE;
import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public abstract class BaseITTracingServerInterceptor extends ITRemote {
  GrpcTracing grpcTracing = GrpcTracing.create(tracing);
  Server server;
  ManagedChannel client;

  @Before public void setup() throws IOException {
    init();
  }

  void init() throws IOException {
    init(null);
  }

  void init(@Nullable ServerInterceptor userInterceptor) throws IOException {
    stop();

    // tracing interceptor needs to go last
    ServerInterceptor tracingInterceptor = grpcTracing.newServerInterceptor();
    ServerInterceptor[] interceptors = userInterceptor != null
      ? new ServerInterceptor[] {userInterceptor, tracingInterceptor}
      : new ServerInterceptor[] {tracingInterceptor};

    server = ServerBuilder.forPort(PickUnusedPort.get())
      .addService(ServerInterceptors.intercept(new GreeterImpl(grpcTracing), interceptors))
      .build().start();

    client = usePlainText(ManagedChannelBuilder.forAddress("localhost", server.getPort()))
      .build();
  }

  /** Extracted as {@link ManagedChannelBuilder#usePlaintext()} is a version-specific signature */
  protected abstract ManagedChannelBuilder<?> usePlainText(ManagedChannelBuilder<?> localhost);

  @After public void stop() {
    try {
      if (client != null) {
        client.shutdown();
        client.awaitTermination(1, TimeUnit.SECONDS);
      }
      if (server != null) {
        server.shutdown();
        server.awaitTermination();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }

  @Test public void reusesPropagatedSpanId() {
    TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);
    Channel channel = clientWithB3SingleHeader(parent);
    GreeterGrpc.newBlockingStub(channel).sayHello(HELLO_REQUEST);

    assertSameIds(reporter.takeRemoteSpan(Span.Kind.SERVER), parent);
  }

  @Test public void createsChildWhenJoinDisabled() throws IOException {
    tracing = tracingBuilder(NEVER_SAMPLE).supportsJoin(false).build();
    grpcTracing = GrpcTracing.create(tracing);
    init();

    TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);
    Channel channel = clientWithB3SingleHeader(parent);
    GreeterGrpc.newBlockingStub(channel).sayHello(HELLO_REQUEST);

    assertChildOf(reporter.takeRemoteSpan(Span.Kind.SERVER), parent);
  }

  @Test public void samplingDisabled() throws IOException {
    tracing = tracingBuilder(NEVER_SAMPLE).build();
    grpcTracing = GrpcTracing.create(tracing);
    init();

    GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);

    // @After will check that nothing is reported
  }

  /**
   * NOTE: for this to work, the tracing interceptor must be last (so that it executes first)
   *
   * <p>Also notice that we are only making the current context available in the request side.
   */
  @Test public void currentSpanVisibleToUserInterceptors() throws IOException {
    AtomicReference<TraceContext> fromUserInterceptor = new AtomicReference<>();
    init(new ServerInterceptor() {
      @Override
      public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
        Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        fromUserInterceptor.set(tracing.currentTraceContext().get());
        return next.startCall(call, headers);
      }
    });

    GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);

    assertThat(fromUserInterceptor.get())
      .isNotNull();

    reporter.takeRemoteSpan(Span.Kind.SERVER);
  }

  @Test public void reportsServerKindToZipkin() {
    GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);

    reporter.takeRemoteSpan(Span.Kind.SERVER);
  }

  @Test public void defaultSpanNameIsMethodName() {
    GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);

    assertThat(reporter.takeRemoteSpan(Span.Kind.SERVER).name())
      .isEqualTo("helloworld.greeter/sayhello");
  }

  /** {@link GreeterImpl} is trained to throw an {@link IllegalArgumentException} on error */
  @Test public void addsErrorTagOnException() {
    assertThatThrownBy(() -> GreeterGrpc.newBlockingStub(client)
      .sayHello(HelloRequest.newBuilder().setName("bad").build()));

    Span span = reporter.takeRemoteSpanWithError(Span.Kind.SERVER, "IllegalArgumentException");
    assertThat(span.tags()).containsEntry("grpc.status_code", "UNKNOWN");
  }

  @Test public void addsErrorTagOnRuntimeException() {
    assertThatThrownBy(() -> GreeterGrpc.newBlockingStub(client)
      .sayHello(HelloRequest.newBuilder().setName("testerror").build()))
      .isInstanceOf(StatusRuntimeException.class);

    Span span = reporter.takeRemoteSpanWithError(Span.Kind.SERVER, "testerror");
    assertThat(span.tags().get("grpc.status_code")).isNull();
  }

  @Test
  public void serverParserTest() throws IOException {
    grpcTracing = grpcTracing.toBuilder().serverParser(new GrpcServerParser() {
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
    init();

    GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);

    Span span = reporter.takeRemoteSpan(Span.Kind.SERVER);
    assertThat(span.name()).isEqualTo("unary");
    assertThat(span.tags().keySet()).containsExactlyInAnyOrder(
      "grpc.message_received", "grpc.message_sent",
      "grpc.message_received.visible", "grpc.message_sent.visible"
    );
  }

  @Test public void serverParserTestWithStreamingResponse() throws IOException {
    grpcTracing = grpcTracing.toBuilder().serverParser(new GrpcServerParser() {
      int responsesSent = 0;

      @Override protected <M> void onMessageSent(M message, SpanCustomizer span) {
        span.tag("grpc.message_sent." + responsesSent++, message.toString());
      }
    }).build();
    init();

    Iterator<HelloReply> replies = GreeterGrpc.newBlockingStub(client)
      .sayHelloWithManyReplies(HELLO_REQUEST);
    assertThat(replies).toIterable().hasSize(10);
    // all response messages are tagged to the same span
    assertThat(reporter.takeRemoteSpan(Span.Kind.SERVER).tags()).hasSize(10);
  }

  @Test public void customSampler() throws IOException {
    RpcTracing rpcTracing = RpcTracing.newBuilder(tracing).serverSampler(RpcRuleSampler.newBuilder()
      .putRule(methodEquals("SayHelloWithManyReplies"), NEVER_SAMPLE)
      .putRule(serviceEquals("helloworld.greeter"), ALWAYS_SAMPLE)
      .build()).build();
    grpcTracing = GrpcTracing.create(rpcTracing);
    init();

    // unsampled
    // NOTE: An iterator request is lazy: invoking the iterator invokes the request
    GreeterGrpc.newBlockingStub(client).sayHelloWithManyReplies(HELLO_REQUEST).hasNext();

    // sampled
    GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);

    assertThat(reporter.takeRemoteSpan(Span.Kind.SERVER).name())
      .isEqualTo("helloworld.greeter/sayhello");

    // @After will also check that sayHelloWithManyReplies was not sampled
  }

  // Make sure we work well with bad user interceptors.

  @Test public void userInterceptor_ThrowsOnStartCall() throws IOException {
    init(new ServerInterceptor() {
      @Override public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
        ServerCall<ReqT, RespT> call, Metadata metadata, ServerCallHandler<ReqT, RespT> next) {
        throw new IllegalStateException("I'm a bad interceptor.");
      }
    });

    assertThatThrownBy(() -> GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST))
      .isInstanceOf(StatusRuntimeException.class);
    reporter.takeRemoteSpanWithError(Span.Kind.SERVER, "I'm a bad interceptor.");
  }

  @Test public void userInterceptor_throwsOnSendMessage() throws IOException {
    init(new ServerInterceptor() {
      @Override public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
        ServerCall<ReqT, RespT> call, Metadata metadata, ServerCallHandler<ReqT, RespT> next) {
        return next.startCall(new SimpleForwardingServerCall<ReqT, RespT>(call) {
          @Override public void sendMessage(RespT message) {
            throw new IllegalStateException("I'm a bad interceptor.");
          }
        }, metadata);
      }
    });

    assertThatThrownBy(() -> GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST))
      .isInstanceOf(StatusRuntimeException.class);
    reporter.takeRemoteSpanWithError(Span.Kind.SERVER, "I'm a bad interceptor.");
  }

  @Test public void userInterceptor_throwsOnClose() throws IOException {
    init(new ServerInterceptor() {
      @Override public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
        ServerCall<ReqT, RespT> call, Metadata metadata, ServerCallHandler<ReqT, RespT> next) {
        return next.startCall(new SimpleForwardingServerCall<ReqT, RespT>(call) {
          @Override public void close(Status status, Metadata trailers) {
            throw new IllegalStateException("I'm a bad interceptor.");
          }
        }, metadata);
      }
    });

    assertThatThrownBy(() -> GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST))
      .isInstanceOf(StatusRuntimeException.class);
    reporter.takeRemoteSpanWithError(Span.Kind.SERVER, "I'm a bad interceptor.");
  }

  @Test public void userInterceptor_throwsOnOnHalfClose() throws IOException {
    init(new ServerInterceptor() {
      @Override public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
        ServerCall<ReqT, RespT> call, Metadata metadata, ServerCallHandler<ReqT, RespT> next) {
        return new SimpleForwardingServerCallListener<ReqT>(next.startCall(call, metadata)) {
          @Override public void onHalfClose() {
            throw new IllegalStateException("I'm a bad interceptor.");
          }
        };
      }
    });

    assertThatThrownBy(() -> GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST))
      .isInstanceOf(StatusRuntimeException.class);
    reporter.takeRemoteSpanWithError(Span.Kind.SERVER, "I'm a bad interceptor.");
  }

  /**
   * This shows that a {@link ServerInterceptor} can see the server server span when processing the
   * request and response.
   */
  @Test public void bodyTaggingExample() throws IOException {
    SpanCustomizer customizer = CurrentSpanCustomizer.create(tracing);
    AtomicInteger sends = new AtomicInteger();
    AtomicInteger recvs = new AtomicInteger();

    init(new ServerInterceptor() {
      @Override
      public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
        ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        call = new SimpleForwardingServerCall<ReqT, RespT>(call) {
          @Override public void sendMessage(RespT message) {
            delegate().sendMessage(message);
            customizer.tag("grpc.message_send." + sends.getAndIncrement(), message.toString());
          }
        };
        return new SimpleForwardingServerCallListener<ReqT>(next.startCall(call, headers)) {
          @Override public void onMessage(ReqT message) {
            customizer.tag("grpc.message_recv." + recvs.getAndIncrement(), message.toString());
            delegate().onMessage(message);
          }
        };
      }
    });

    GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);

    assertThat(reporter.takeRemoteSpan(Span.Kind.SERVER).tags()).containsKeys(
      "grpc.message_recv.0", "grpc.message_send.0"
    );

    Iterator<HelloReply> replies = GreeterGrpc.newBlockingStub(client)
      .sayHelloWithManyReplies(HELLO_REQUEST);
    assertThat(replies).toIterable().hasSize(10);

    // Intentionally verbose here to show that only one recv and 10 replies
    assertThat(reporter.takeRemoteSpan(Span.Kind.SERVER).tags()).containsKeys(
      "grpc.message_recv.1",
      "grpc.message_send.1",
      "grpc.message_send.2",
      "grpc.message_send.3",
      "grpc.message_send.4",
      "grpc.message_send.5",
      "grpc.message_send.6",
      "grpc.message_send.7",
      "grpc.message_send.8",
      "grpc.message_send.9",
      "grpc.message_send.10"
    );
  }

  Channel clientWithB3SingleHeader(TraceContext parent) {
    return ClientInterceptors.intercept(client, new ClientInterceptor() {
      @Override public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
        MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        return new SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
          @Override public void start(Listener<RespT> responseListener, Metadata headers) {
            headers.put(Key.of("b3", ASCII_STRING_MARSHALLER),
              B3SingleFormat.writeB3SingleFormat(parent));
            super.start(responseListener, headers);
          }
        };
      }
    });
  }
}

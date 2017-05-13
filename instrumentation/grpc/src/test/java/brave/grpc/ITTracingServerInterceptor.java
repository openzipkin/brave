package brave.grpc;

import brave.Tracing;
import brave.internal.HexCodec;
import brave.internal.StrictCurrentTraceContext;
import brave.sampler.Sampler;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import io.grpc.StatusRuntimeException;
import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.HelloRequest;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import zipkin.Constants;
import zipkin.Span;
import zipkin.internal.Util;

import static brave.grpc.GreeterImpl.HELLO_REQUEST;
import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.assertj.core.api.Assertions.tuple;

public class ITTracingServerInterceptor {

  @Rule public ExpectedException thrown = ExpectedException.none();

  ConcurrentLinkedDeque<Span> spans = new ConcurrentLinkedDeque<>();

  Tracing tracing;
  Server server;
  ManagedChannel client;

  @Before public void setup() throws Exception {
    tracing = tracingBuilder(Sampler.ALWAYS_SAMPLE).build();
    init();
  }

  void init() throws Exception {
    stop();

    server = ServerBuilder.forPort(PickUnusedPort.get())
        .addService(ServerInterceptors.intercept(new GreeterImpl(),
            GrpcTracing.create(tracing).newServerInterceptor()))
        .build().start();

    client = ManagedChannelBuilder.forAddress("localhost", server.getPort())
        .usePlaintext(true)
        .build();
  }

  @After
  public void stop() throws Exception {
    if (client != null) {
      client.shutdown();
      client.awaitTermination(1, TimeUnit.SECONDS);
    }
    if (server != null) {
      server.shutdown();
      server.awaitTermination();
    }
  }

  @Test
  public void usesExistingTraceId() throws Exception {
    final String traceId = "463ac35c9f6413ad";
    final String parentId = traceId;
    final String spanId = "48485a3953bb6124";

    Channel channel = ClientInterceptors.intercept(client, new ClientInterceptor() {
      @Override
      public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
          MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        return new SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
          @Override
          public void start(Listener<RespT> responseListener, Metadata headers) {
            headers.put(Key.of("X-B3-TraceId", ASCII_STRING_MARSHALLER), traceId);
            headers.put(Key.of("X-B3-ParentSpanId", ASCII_STRING_MARSHALLER), parentId);
            headers.put(Key.of("X-B3-SpanId", ASCII_STRING_MARSHALLER), spanId);
            super.start(responseListener, headers);
          }
        };
      }
    });

    GreeterGrpc.newBlockingStub(channel).sayHello(HELLO_REQUEST);

    assertThat(spans).allSatisfy(s -> {
      assertThat(HexCodec.toLowerHex(s.traceId)).isEqualTo(traceId);
      assertThat(HexCodec.toLowerHex(s.parentId)).isEqualTo(parentId);
      assertThat(HexCodec.toLowerHex(s.id)).isEqualTo(spanId);
    });
  }

  @Test
  public void samplingDisabled() throws Exception {
    tracing = tracingBuilder(Sampler.NEVER_SAMPLE).build();
    init();

    GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);

    assertThat(spans)
        .isEmpty();
  }

  @Test
  public void reportsServerAnnotationsToZipkin() throws Exception {
    GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);

    assertThat(spans)
        .flatExtracting(s -> s.annotations)
        .extracting(a -> a.value)
        .containsExactly("sr", "ss");
  }

  @Test
  public void defaultSpanNameIsMethodName() throws Exception {
    GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);

    assertThat(spans)
        .extracting(s -> s.name)
        .containsExactly("helloworld.greeter/sayhello");
  }

  @Test
  public void addsErrorTagOnException() throws Exception {
    try {
      GreeterGrpc.newBlockingStub(client)
          .sayHello(HelloRequest.newBuilder().setName("bad").build());
      failBecauseExceptionWasNotThrown(StatusRuntimeException.class);
    } catch (StatusRuntimeException e) {
      assertThat(spans)
          .flatExtracting(s -> s.binaryAnnotations)
          .extracting(b -> tuple(b.key, new String(b.value, Util.UTF_8)))
          .contains(tuple(Constants.ERROR, e.getStatus().getCode().toString()));
    }
  }

  Tracing.Builder tracingBuilder(Sampler sampler) {
    return Tracing.newBuilder()
        .reporter(spans::add)
        .currentTraceContext(new StrictCurrentTraceContext())
        .sampler(sampler);
  }
}

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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import zipkin.BinaryAnnotation;
import zipkin.Constants;
import zipkin.Endpoint;
import zipkin.Span;
import zipkin.storage.InMemoryStorage;

import static brave.grpc.GreeterImpl.HELLO_REQUEST;
import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

public class ITTracingServerInterceptor {

  @Rule public ExpectedException thrown = ExpectedException.none();

  Endpoint local = Endpoint.builder().serviceName("local").ipv4(127 << 24 | 1).port(100).build();
  InMemoryStorage storage = new InMemoryStorage();

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

    assertThat(collectedSpans()).allSatisfy(s -> {
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

    assertThat(collectedSpans())
        .isEmpty();
  }

  @Test
  public void reportsServerAnnotationsToZipkin() throws Exception {
    GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);

    assertThat(collectedSpans())
        .flatExtracting(s -> s.annotations)
        .extracting(a -> a.value)
        .containsExactly("sr", "ss");
  }

  @Test
  public void defaultSpanNameIsMethodName() throws Exception {
    GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);

    assertThat(collectedSpans())
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
      assertThat(collectedSpans())
          .flatExtracting(s -> s.binaryAnnotations)
          .contains(
              BinaryAnnotation.create(Constants.ERROR, e.getStatus().getCode().toString(), local));
    }
  }

  Tracing.Builder tracingBuilder(Sampler sampler) {
    return Tracing.newBuilder()
        .reporter(s -> storage.spanConsumer().accept(asList(s)))
        .currentTraceContext(new StrictCurrentTraceContext())
        .localEndpoint(local)
        .sampler(sampler);
  }

  List<Span> collectedSpans() {
    List<List<Span>> result = storage.spanStore().getRawTraces();
    if (result.isEmpty()) return Collections.emptyList();
    assertThat(result).hasSize(1);
    return result.get(0);
  }
}

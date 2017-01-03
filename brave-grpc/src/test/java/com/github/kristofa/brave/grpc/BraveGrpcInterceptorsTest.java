package com.github.kristofa.brave.grpc;

import static com.github.kristofa.brave.grpc.GrpcKeys.GRPC_STATUS_CODE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.atIndex;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.LocalTracer;
import com.github.kristofa.brave.Sampler;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.ThreadLocalServerClientAndLocalSpanState;
import com.github.kristofa.brave.internal.InternalSpan;
import com.github.kristofa.brave.internal.Util;
import com.google.common.util.concurrent.ListenableFuture;
import com.twitter.zipkin.gen.Span;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.GreeterGrpc.GreeterBlockingStub;
import io.grpc.examples.helloworld.GreeterGrpc.GreeterFutureStub;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;

import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.Condition;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.ExecutionException;
import zipkin.Constants;
import zipkin.storage.InMemoryStorage;
import zipkin.storage.QueryRequest;

public class BraveGrpcInterceptorsTest {
    static {
        InternalSpan.initializeInstanceForTests();
    }

    static final HelloRequest HELLO_REQUEST = HelloRequest.newBuilder()
        .setName("brave")
        .build();

    Server server;
    ManagedChannel channel;
    InMemoryStorage storage = new InMemoryStorage();
    Brave brave = new Brave.Builder()
        .traceSampler(new ExplicitSampler())
        .reporter(s -> storage.spanConsumer().accept(Collections.singletonList(s))).build();
    boolean enableSampling;

    @Before
    public void before() throws Exception {
        enableSampling = true;
        ThreadLocalServerClientAndLocalSpanState.clear();

        int serverPort = pickUnusedPort();
        server = ServerBuilder.forPort(serverPort)
            .addService(ServerInterceptors.intercept(new GreeterImpl(), BraveGrpcServerInterceptor.create(brave)))
            .build()
            .start();

        channel = ManagedChannelBuilder.forAddress("localhost", serverPort)
            .intercept(BraveGrpcClientInterceptor.create(brave))
            .usePlaintext(true)
            .build();
    }

    @Test
    public void testBlockingUnaryCall() throws Exception {
        GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(channel);
        HelloReply reply = stub.sayHello(HELLO_REQUEST);
        assertThat(reply.getMessage()).isEqualTo("Hello brave");
        validateSpans();
    }

    @Test
    public void testAsyncUnaryCall() throws Exception {
        GreeterFutureStub futureStub = GreeterGrpc.newFutureStub(channel);
        ListenableFuture<HelloReply> helloReplyListenableFuture = futureStub.sayHello(HELLO_REQUEST);
        HelloReply reply = helloReplyListenableFuture.get();
        assertThat(reply.getMessage()).isEqualTo("Hello brave");
        validateSpans();
    }

    @Test
    public void statusCodeAddedOnError() throws Exception {
        tearDown(); // kill the server
        GreeterFutureStub futureStub = GreeterGrpc.newFutureStub(channel);
        ListenableFuture<HelloReply> helloReplyListenableFuture = futureStub.sayHello(HELLO_REQUEST);
        try {
            helloReplyListenableFuture.get();
            failBecauseExceptionWasNotThrown(ExecutionException.class);
        } catch (ExecutionException expected) {
            List<List<zipkin.Span>> traces = storage.spanStore()
                .getTraces(QueryRequest.builder().build());
            assertThat(traces).hasSize(1);
            List<zipkin.Span> spans = traces.get(0);
            assertThat(spans.size()).isEqualTo(1);

            assertThat(spans.get(0).binaryAnnotations)
                .filteredOn(ba -> ba.key.equals(GRPC_STATUS_CODE))
                .extracting(ba -> new String(ba.value, Util.UTF_8))
                .containsExactly(Status.UNAVAILABLE.getCode().name());
        }
    }

    @Test
    public void usesExistingTraceId() throws Exception {
        LocalTracer localTracer = brave.localTracer();
        SpanId spanId = localTracer.startNewSpan("localSpan", "myop");
        GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(channel);
        //This call will be made using hte context of the localTracer as it's parent
        HelloReply reply = stub.sayHello(HELLO_REQUEST);
        assertThat(reply.getMessage()).isEqualTo("Hello brave");

        assertOnlyOneSpan();
    }

    /**
     * This test verifies that the sampling rate determined by the client is correctly propagated to the server.
     * Since sampling is disabled in by the client, then there should be no span generated by the server.
     */
    @Test
    public void noSamplesWhenSamplingDisabled() throws Exception {
        enableSampling = false;
        GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(channel);
        HelloReply reply = stub.sayHello(HELLO_REQUEST);
        assertThat(reply.getMessage()).isEqualTo("Hello brave");

        assertThat(storage.spanStore().getRawTraces()).isEmpty();
    }

    @Test
    public void propagatesAndReads128BitTraceId() throws Exception {
        SpanId spanId = SpanId.builder().traceIdHigh(1).traceId(2).spanId(3).parentId(2L).build();
        brave.localSpanThreadBinder().setCurrentSpan(InternalSpan.instance.toSpan(spanId));

        GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(channel);
        //This call will be made using hte context of the localTracer as it's parent
        HelloReply reply = stub.sayHello(HELLO_REQUEST);
        assertThat(reply.getMessage()).isEqualTo("Hello brave");

        //Verify that the 128-bit trace id and span id were propagated to the server
        zipkin.Span clientServerSpan = assertOnlyOneSpan();
        assertThat(clientServerSpan.traceIdHigh).isEqualTo(spanId.traceIdHigh);
        assertThat(clientServerSpan.traceId).isEqualTo(spanId.traceId);
        assertThat(clientServerSpan.parentId).isEqualTo(spanId.spanId);
    }

    /**
     * Validating that two spans were generated indicates that a span was generated by both the
     * server and the client.
     */
    void validateSpans() throws Exception {
        tearDown(); // make sure any in-flight requests complete, so that the below doesn't flake
        zipkin.Span clientServerSpan = assertOnlyOneSpan();
        assertThat(clientServerSpan.annotations).extracting(a -> a.value)
            .containsExactly("cs", "sr", "ss", "cr");

        //Server spans should have the GRPC_REMOTE_ADDR binary annotation
        assertThat(clientServerSpan.binaryAnnotations)
            .filteredOn(ba -> ba.key.equals(GrpcKeys.GRPC_REMOTE_ADDR))
            .extracting(ba -> new String(ba.value, Util.UTF_8))
            .has(new Condition<>(b -> b.matches("^/127.0.0.1:[\\d]+$"), "a local IP address"), atIndex(0));

        //Server spans should have the client address binary annotation
        assertThat(clientServerSpan.binaryAnnotations)
            .filteredOn(ba -> ba.key.equals(Constants.CLIENT_ADDR))
            .extracting(b -> b.endpoint.port)
            .doesNotContainNull(); // if we got a port, we also got an ipv4 or ipv6
    }

    private zipkin.Span assertOnlyOneSpan() {
        List<List<zipkin.Span>> traces = storage.spanStore()
            .getTraces(QueryRequest.builder().build());
        assertThat(traces).hasSize(1);
        assertThat(traces.get(0))
            .withFailMessage("Expected client and server to share ids: " + traces.get(0))
            .hasSize(1);

        return traces.get(0).get(0);
    }

    void validateSpan(Span span, Iterable<String> expectedAnnotations) {
        assertThat(span.getName()).isEqualTo("helloworld.greeter/sayhello");
        assertThat(span.getAnnotations())
            .extracting(a -> a.value)
            .containsExactlyElementsOf(expectedAnnotations);
    }

    @After
    public void tearDown() throws InterruptedException {
        if (!channel.isShutdown()) {
            channel.shutdownNow();
            channel.awaitTermination(1, TimeUnit.SECONDS);
        }
        if (!server.isShutdown()) {
            server.shutdownNow();
            server.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    public static int pickUnusedPort() {
        try {
            ServerSocket serverSocket = new ServerSocket(0);
            int port = serverSocket.getLocalPort();
            serverSocket.close();
            return port;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    class ExplicitSampler extends Sampler {

        @Override
        public boolean isSampled(long traceId) {
            return enableSampling;
        }
    }
}

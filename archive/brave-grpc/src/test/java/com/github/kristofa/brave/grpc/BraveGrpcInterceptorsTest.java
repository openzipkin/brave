package com.github.kristofa.brave.grpc;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.LocalTracer;
import com.github.kristofa.brave.Sampler;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.ThreadLocalServerClientAndLocalSpanState;
import com.github.kristofa.brave.internal.InternalSpan;
import com.google.common.util.concurrent.ListenableFuture;
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
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import zipkin2.Span.Kind;
import zipkin2.storage.InMemoryStorage;

import static com.github.kristofa.brave.grpc.GrpcKeys.GRPC_STATUS_CODE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

public class BraveGrpcInterceptorsTest {
    static {
        InternalSpan.initializeInstanceForTests();
    }

    static final HelloRequest HELLO_REQUEST = HelloRequest.newBuilder()
        .setName("brave")
        .build();

    Server server;
    ManagedChannel channel;
    InMemoryStorage storage = InMemoryStorage.newBuilder().build();
    Brave brave = new Brave.Builder()
        .traceSampler(new ExplicitSampler())
        .spanReporter(s -> storage.spanConsumer().accept(Collections.singletonList(s))).build();
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

        // TODO: only validate client side as for some reason this flakes in travis on server side
        List<zipkin2.Span> clientServerSpan = spans();
        assertThat(clientServerSpan)
            .flatExtracting(zipkin2.Span::kind)
            .contains(Kind.CLIENT); // not contains exactly
    }

    @Test
    public void testAsyncUnaryCall() throws Exception {
        GreeterFutureStub futureStub = GreeterGrpc.newFutureStub(channel);
        ListenableFuture<HelloReply> helloReplyListenableFuture = futureStub.sayHello(HELLO_REQUEST);
        HelloReply reply = helloReplyListenableFuture.get();
        assertThat(reply.getMessage()).isEqualTo("Hello brave");

        List<zipkin2.Span> clientServerSpan = spans();
        assertThat(clientServerSpan)
            .flatExtracting(zipkin2.Span::kind)
            .containsOnly(Kind.SERVER, Kind.CLIENT);
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
            List<List<zipkin2.Span>> traces = storage.spanStore().getTraces();
            assertThat(traces).hasSize(1);
            List<zipkin2.Span> spans = traces.get(0);
            assertThat(spans.size()).isEqualTo(1);

            assertThat(spans.get(0).tags().entrySet())
                .contains(entry(GRPC_STATUS_CODE, Status.UNAVAILABLE.getCode().name()));
        }
    }

    @Test
    public void usesExistingTraceId() throws Exception {
        LocalTracer localTracer = brave.localTracer();
        SpanId spanId = localTracer.startNewSpan("localSpan", "myop");
        GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(channel);
        //This call will be made using the context of the localTracer as it's parent
        HelloReply reply = stub.sayHello(HELLO_REQUEST);
        assertThat(reply.getMessage()).isEqualTo("Hello brave");

        for (zipkin2.Span span : spans()) {
            assertThat(span.traceId()).isEqualTo(String.format("%016x", spanId.traceId));
            assertThat(span.parentId()).isEqualTo(String.format("%016x", spanId.spanId));
        }
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

        assertThat(storage.spanStore().getTraces()).isEmpty();
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
        for (zipkin2.Span span : spans()) {
            assertThat(span.traceId())
                .isEqualTo(String.format("%016x%016x", spanId.traceIdHigh, spanId.traceId));
            assertThat(span.parentId()).isEqualTo(String.format("%016x", spanId.spanId));
        }
    }

    /**
     * Validating that two spans were generated indicates that a span was generated by both the
     * server and the client.
     */
    void assertClientServerSpan() throws Exception {
        List<zipkin2.Span> clientServerSpan = spans();
        assertThat(clientServerSpan)
            .flatExtracting(zipkin2.Span::kind)
            .containsOnly(Kind.SERVER, Kind.CLIENT);

        assertThat(clientServerSpan)
            .filteredOn(s -> s.kind() == Kind.SERVER)
            .flatExtracting(zipkin2.Span::remoteEndpoint)
            .doesNotContainNull();
    }

    List<zipkin2.Span> spans() throws InterruptedException {
        List<List<zipkin2.Span>> traces = storage.spanStore().getTraces();
        assertThat(traces).hasSize(1);
        assertThat(traces.get(0))
            .withFailMessage("Expected client and server to share ids: " + traces.get(0))
            .hasSize(2);

        return traces.get(0);
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

    static int pickUnusedPort() {
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

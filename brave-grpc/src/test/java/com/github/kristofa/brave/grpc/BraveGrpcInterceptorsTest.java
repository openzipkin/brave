package com.github.kristofa.brave.grpc;

import static com.github.kristofa.brave.grpc.GrpcKeys.GRPC_STATUS_CODE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.atIndex;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.LocalTracer;
import com.github.kristofa.brave.Sampler;
import com.github.kristofa.brave.SpanId;
import com.google.common.util.concurrent.ListenableFuture;
import com.twitter.zipkin.gen.BinaryAnnotation;
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

import org.assertj.core.api.Condition;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public class BraveGrpcInterceptorsTest {

    static final HelloRequest HELLO_REQUEST = HelloRequest.newBuilder()
        .setName("brave")
        .build();

    Server server;
    ManagedChannel channel;
    Brave brave;
    boolean enableSampling;

    @Before
    public void before() throws Exception {
        enableSampling = true;
        SpanCollectorForTesting.INSTANCE.clear();

        final Brave.Builder builder = new Brave.Builder();
        brave = builder
            .spanCollector(SpanCollectorForTesting.INSTANCE)
            .traceSampler(new ExplicitSampler())
            .build();

        int serverPort = pickUnusedPort();
        server = ServerBuilder.forPort(serverPort)
            .addService(ServerInterceptors.intercept(new GreeterImpl(), new BraveGrpcServerInterceptor(brave)))
            .build()
            .start();

        channel = ManagedChannelBuilder.forAddress("localhost", serverPort)
            .intercept(new BraveGrpcClientInterceptor(brave))
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
            fail();
        } catch (ExecutionException expected) {
            List<Span> spans = SpanCollectorForTesting.INSTANCE.getCollectedSpans();
            assertThat(spans.size()).isEqualTo(1);
            BinaryAnnotation binaryAnnotation = spans.get(0).getBinary_annotations().get(0);
            assertThat(binaryAnnotation.getKey()).isEqualTo(GRPC_STATUS_CODE);
            assertThat(new String(binaryAnnotation.getValue(), StandardCharsets.UTF_8)).isEqualTo(Status.UNAVAILABLE.getCode().name());
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
        validateSpans();
        List<Span> spans = SpanCollectorForTesting.INSTANCE.getCollectedSpans();
        Optional<Span> maybeSpan = spans.stream()
            .filter(s -> s.getAnnotations().stream().anyMatch(a -> "ss".equals(a.value)))
            .findFirst();
        assertTrue("Could not find expected server span", maybeSpan.isPresent());
        Span span = maybeSpan.get();
        //Verify that the localTracer's trace id and span id were propagated to the server
        assertThat(span.getTrace_id()).isEqualTo(spanId.traceId);
        assertThat(span.getParent_id()).isEqualTo(spanId.spanId);
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
        List<Span> spans = SpanCollectorForTesting.INSTANCE.getCollectedSpans();
        assertThat(spans.size()).isEqualTo(0);
    }

    /**
     * Validating that two spans were generated indicates that a span was generated by both the
     * server and the client.
     */
    void validateSpans() throws Exception {
        List<Span> spans = SpanCollectorForTesting.INSTANCE.getCollectedSpans();
        assertThat(spans.size()).isEqualTo(2);

        Span serverSpan = spans.get(0);
        assertThat(serverSpan.getTrace_id()).isEqualTo(spans.get(1).getTrace_id());
        validateSpan(serverSpan, Arrays.asList("sr", "ss"));

        //Server spans should have the GRPC_REMOTE_ADDR binary annotation
        assertThat(serverSpan.getBinary_annotations())
            .filteredOn(b -> b.key.equals(GrpcKeys.GRPC_REMOTE_ADDR))
            .extracting(b -> new String(b.value))
            .has(new Condition<>(b -> b.matches("^/127.0.0.1:[\\d]+$"), "a local IP address"), atIndex(0));

        validateSpan(spans.get(1), Arrays.asList("cs", "cr"));
    }

    void validateSpan(Span span, Iterable<String> expectedAnnotations) {
        assertThat(span.getName()).isEqualTo("helloworld.greeter/sayhello");
        assertThat(span.getAnnotations())
            .extracting(a -> a.value)
            .containsExactlyElementsOf(expectedAnnotations);
    }

    @After
    public void tearDown() {
        channel.shutdownNow();
        server.shutdownNow();
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

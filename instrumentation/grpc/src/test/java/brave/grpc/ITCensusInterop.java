package brave.grpc;

import brave.ScopedSpan;
import brave.Tracing;
import brave.internal.Nullable;
import brave.propagation.B3Propagation;
import brave.propagation.StrictScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.propagation.TraceContext;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import io.grpc.netty.InternalNettyChannelBuilder;
import io.grpc.netty.InternalNettyServerBuilder;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import io.opencensus.common.Scope;
import io.opencensus.contrib.grpc.metrics.RpcMeasureConstants;
import io.opencensus.implcore.tags.TagContextImpl;
import io.opencensus.tags.TagKey;
import io.opencensus.tags.TagValue;
import io.opencensus.tags.Tags;
import io.opencensus.testing.export.TestHandler;
import io.opencensus.trace.config.TraceParams;
import io.opencensus.trace.export.SpanData;
import io.opencensus.trace.samplers.Samplers;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import zipkin2.Span;

import static brave.grpc.GreeterImpl.HELLO_REQUEST;
import static io.grpc.ServerInterceptors.intercept;
import static io.opencensus.trace.AttributeValue.stringAttributeValue;
import static io.opencensus.trace.Tracing.getExportComponent;
import static io.opencensus.trace.Tracing.getTraceConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class ITCensusInterop {

  static class TagsGreeterImpl extends GreeterImpl {
    TagsGreeterImpl(@Nullable GrpcTracing grpcTracing) {
      super(grpcTracing);
    }

    /** This verifies internal state by writing to both brave and census apis */
    @Override
    public void sayHello(HelloRequest req, StreamObserver<HelloReply> responseObserver) {
      Map<TagKey, TagValue> censusTags =
          ((TagContextImpl) Tags.getTagger().getCurrentTagContext()).getTags();

      // Read in-process tags from census and write to both span apis
      io.opencensus.trace.Span censusSpan =
          io.opencensus.trace.Tracing.getTracer().getCurrentSpan();
      for (Map.Entry<TagKey, TagValue> entry : censusTags.entrySet()) {
        spanCustomizer.tag(entry.getKey().getName(), entry.getValue().asString());
        censusSpan.putAttribute(
            entry.getKey().getName(), stringAttributeValue(entry.getValue().asString()));
      }

      // Read in-process tags from brave's grpc hooks and write to both span apis
      TraceContext currentTraceContext =
          tracing != null ? tracing.currentTraceContext().get() : null;
      if (currentTraceContext == null) {
        super.sayHello(req, responseObserver);
        return;
      }
      ScopedSpan child = tracing.tracer().startScopedSpanWithParent("child", currentTraceContext);
      try {
        GrpcPropagation.Tags tags = child.context().findExtra(GrpcPropagation.Tags.class);

        if (tags.parentMethod != null) {
          child.tag("parentMethod", tags.parentMethod);
          censusSpan.putAttribute("parentMethod", stringAttributeValue(tags.parentMethod));
        }

        for (Map.Entry<String, String> entry : tags.toMap().entrySet()) {
          child.tag(entry.getKey(), entry.getValue());
          censusSpan.putAttribute(entry.getKey(), stringAttributeValue(entry.getValue()));
        }
        super.sayHello(req, responseObserver);
      } finally {
        child.finish();
      }
    }
  }

  final TestHandler testHandler = new TestHandler();

  @Before
  public void beforeClass() {
    getTraceConfig()
        .updateActiveTraceParams(
            TraceParams.DEFAULT.toBuilder().setSampler(Samplers.alwaysSample()).build());
    getExportComponent().getSpanExporter().registerHandler("test", testHandler);
  }

  /** See brave.http.ITHttp for rationale on using a concurrent blocking queue */
  BlockingQueue<Span> spans = new LinkedBlockingQueue<>();

  Tracing tracing =
      Tracing.newBuilder()
          .propagationFactory(GrpcPropagation.newFactory(B3Propagation.FACTORY))
          .spanReporter(spans::add)
          .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
              .addScopeDecorator(StrictScopeDecorator.create())
              .build())
          .build();
  GrpcTracing grpcTracing = GrpcTracing.create(tracing);

  Server server;
  ManagedChannel client;

  @Test
  public void readsCensusPropagation() throws Exception {
    initServer(true); // trace server with brave
    initClient(false); // trace client with census

    GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);

    // this takes 5 seconds due to hard-coding in ExportComponentImpl
    SpanData clientSpan = testHandler.waitForExport(1).get(0);

    Span serverSpan = takeSpan();
    assertThat(clientSpan.getContext().getTraceId().toLowerBase16())
        .isEqualTo(serverSpan.traceId());
    assertThat(clientSpan.getContext().getSpanId().toLowerBase16())
        .isEqualTo(serverSpan.parentId());
    assertThat(serverSpan.tags()).containsEntry("method", "helloworld.Greeter/SayHello");
  }

  @Test
  public void readsCensusPropagation_withIncomingMethod() throws Exception {
    initServer(true); // trace server with brave
    initClient(false); // trace client with census

    try (Scope tagger =
             Tags.getTagger()
                 .emptyBuilder()
                 .put(RpcMeasureConstants.RPC_METHOD, TagValue.create("edge.Ingress/InitialRoute"))
                 .buildScoped()) {
      GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);
    }

    // this takes 5 seconds due to hard-coding in ExportComponentImpl
    SpanData clientSpan = testHandler.waitForExport(1).get(0);

    Span serverSpan = takeSpan(), childSpan = takeSpan();
    assertThat(clientSpan.getContext().getTraceId().toLowerBase16())
        .isEqualTo(serverSpan.traceId());
    assertThat(clientSpan.getContext().getSpanId().toLowerBase16())
        .isEqualTo(serverSpan.parentId());
    assertThat(serverSpan.tags()).containsExactly(
        entry("method", "helloworld.Greeter/SayHello")
    );

    // Show that parentMethod inherits in-process
    assertThat(childSpan.tags()).containsExactly(
        entry("parentMethod", "edge.Ingress/InitialRoute")
    );
  }

  @Test
  public void writesCensusPropagation() throws Exception {
    initServer(false); // trace server with census
    initClient(true); // trace client with brave

    GreeterGrpc.newBlockingStub(client).sayHello(HELLO_REQUEST);

    // this takes 5 seconds due to hard-coding in ExportComponentImpl
    SpanData serverSpan = testHandler.waitForExport(1).get(0);

    Span clientSpan = takeSpan();
    assertThat(clientSpan.traceId())
        .isEqualTo(serverSpan.getContext().getTraceId().toLowerBase16());
    assertThat(clientSpan.id()).isEqualTo(serverSpan.getParentSpanId().toLowerBase16());
    assertThat(serverSpan.getAttributes().getAttributeMap())
        .containsEntry("method", stringAttributeValue("helloworld.Greeter/SayHello"));
  }

  void initServer(boolean traceWithBrave) throws Exception {
    if (traceWithBrave) {
      NettyServerBuilder builder = (NettyServerBuilder) ServerBuilder.forPort(PickUnusedPort.get());
      builder.addService(
          intercept(new TagsGreeterImpl(grpcTracing), grpcTracing.newServerInterceptor()));
      // TODO: track gRPC exposing this
      InternalNettyServerBuilder.setTracingEnabled(builder, false);
      server = builder.build();
    } else {
      server =
          ServerBuilder.forPort(PickUnusedPort.get()).addService(new TagsGreeterImpl(null)).build();
    }
    server.start();
  }

  void initClient(boolean traceWithBrave) {
    if (traceWithBrave) {
      NettyChannelBuilder builder =
          (NettyChannelBuilder)
              ManagedChannelBuilder.forAddress("localhost", server.getPort())
                  .intercept(grpcTracing.newClientInterceptor())
                  .usePlaintext();
      // TODO: track gRPC exposing this
      InternalNettyChannelBuilder.setTracingEnabled(builder, false);
      client = builder.build();
    } else {
      client =
          ManagedChannelBuilder.forAddress("localhost", server.getPort()).usePlaintext().build();
    }
  }

  @After
  public void close() throws Exception {
    if (client != null) {
      client.shutdown();
      client.awaitTermination(1, TimeUnit.SECONDS);
    }
    if (server != null) {
      server.shutdown();
      server.awaitTermination(1, TimeUnit.SECONDS);
    }
    tracing.close();
  }

  /** Call this to block until a span was reported */
  Span takeSpan() throws InterruptedException {
    Span result = spans.poll(3, TimeUnit.SECONDS);
    assertThat(result)
        .withFailMessage("Span was not reported")
        .isNotNull();
    return result;
  }
}

package brave.grpc;

import brave.propagation.B3Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

class TestServer {
  BlockingQueue<Long> delayQueue = new LinkedBlockingQueue<>();
  BlockingQueue<TraceContextOrSamplingFlags> requestQueue = new LinkedBlockingQueue<>();
  TraceContext.Extractor<Metadata> extractor =
      B3Propagation.FACTORY.create(AsciiMetadataKeyFactory.INSTANCE).extractor(Metadata::get);

  Server server = ServerBuilder.forPort(PickUnusedPort.get())
      .addService(ServerInterceptors.intercept(new GreeterImpl(null), new ServerInterceptor() {

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
            Metadata headers, ServerCallHandler<ReqT, RespT> next) {
          Long delay = delayQueue.poll();
          if (delay != null) {
            try {
              Thread.sleep(delay);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new AssertionError("interrupted sleeping " + delay);
            }
          }
          requestQueue.add(extractor.extract(headers));
          return next.startCall(call, headers);
        }
      }))
      .build();

  void start() throws IOException {
    server.start();
  }

  void stop() throws InterruptedException {
    server.shutdown();
    server.awaitTermination();
  }

  int port() {
    return server.getPort();
  }

  TraceContextOrSamplingFlags takeRequest() throws InterruptedException {
    return requestQueue.take();
  }

  void enqueueDelay(long millis) {
    this.delayQueue.add(millis);
  }
}

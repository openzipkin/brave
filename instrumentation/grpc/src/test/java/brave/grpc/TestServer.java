/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.grpc;

import brave.propagation.Propagation;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContextOrSamplingFlags;
import io.grpc.ForwardingServerCall.SimpleForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

class TestServer {
  static final Key<String> CUSTOM_KEY = Key.of("custom", Metadata.ASCII_STRING_MARSHALLER);
  final BlockingQueue<Long> delayQueue = new LinkedBlockingQueue<>();
  final BlockingQueue<Metadata> headers = new LinkedBlockingQueue<>();
  final BlockingQueue<TraceContextOrSamplingFlags> requests = new LinkedBlockingQueue<>();
  final Extractor<GrpcServerRequest> extractor;
  final Server server;

  TestServer(Map<String, Key<String>> nameToKey, Propagation<String> propagation) {
    extractor = propagation.extractor(GrpcServerRequest::propagationField);
    server = ServerBuilder.forPort(PickUnusedPort.get())
        .addService(ServerInterceptors.intercept(
            new GreeterImpl(null),
            new ServerInterceptor() {
              @Override
              public <ReqT, RespT> Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
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
                TestServer.this.headers.add(headers);
                requests.add(extractor.extract(new GrpcServerRequest(nameToKey, call, headers)));
                return next.startCall(new SimpleForwardingServerCall<ReqT, RespT>(call) {
                  @Override public void sendHeaders(Metadata headers) {
                    headers.put(CUSTOM_KEY, "brave");
                    super.sendHeaders(headers);
                  }
                }, headers);
              }
            }))
        .build();
  }

  void start() throws IOException {
    server.start();
  }

  void stop() {
    server.shutdown();
    try {
      server.awaitTermination();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }

  int port() {
    return server.getPort();
  }

  TraceContextOrSamplingFlags takeRequest() {
    try {
      return requests.poll(3, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }

  void enqueueDelay(long millis) {
    this.delayQueue.add(millis);
  }
}

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

import brave.Span;
import brave.Tracer;
import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Injector;
import brave.rpc.RpcRequest;
import brave.sampler.SamplerFunction;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientCall.Listener;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import java.util.Map;

import static brave.grpc.GrpcClientRequest.SETTER;

// not exposed directly as implementation notably changes between versions 1.2 and 1.3
final class TracingClientInterceptor implements ClientInterceptor {
  final Tracer tracer;
  final CurrentTraceContext currentTraceContext;
  final SamplerFunction<RpcRequest> sampler;
  final Injector<GrpcClientRequest> injector;
  final GrpcClientParser parser;
  final Map<String, Metadata.Key<String>> nameToKey;

  TracingClientInterceptor(GrpcTracing grpcTracing) {
    tracer = grpcTracing.rpcTracing.tracing().tracer();
    currentTraceContext = grpcTracing.rpcTracing.tracing().currentTraceContext();
    sampler = grpcTracing.rpcTracing.clientSampler();
    injector = grpcTracing.propagation.injector(SETTER);
    parser = grpcTracing.clientParser;
    nameToKey = grpcTracing.nameToKey;
  }

  /**
   * This sets as span in scope both for the interception and for the start of the request. It does
   * not set a span in scope during the response listener as it is unexpected it would be used at
   * that fine granularity. If users want access to the span in a response listener, they will need
   * to wrap the executor with one that's aware of the current context.
   */
  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
    CallOptions callOptions, Channel next) {
    TraceContext invocationContext = currentTraceContext.get();

    GrpcClientRequest request = new GrpcClientRequest(nameToKey, method);
    Span span = tracer.nextSpanWithParent(sampler, request, invocationContext);

    Throwable error = null;
    try (Scope scope = currentTraceContext.maybeScope(span.context())) {
      return new SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
        @Override public void start(Listener<RespT> responseListener, Metadata headers) {
          request.metadata = headers;
          injector.inject(span.context(), request);
          span.kind(Span.Kind.CLIENT).start();
          try (Scope scope = currentTraceContext.maybeScope(span.context())) {
            parser.onStart(method, callOptions, headers, span.customizer());

            // Ensures callbacks execute on the invocation context. For example, if a user has code
            // to invoke a producer once headers are received, this ensures the producer is a child
            // of the invocation (usually server) as opposed to the child of the client.
            responseListener = new TraceContextCallListener<>(
              responseListener,
              currentTraceContext,
              invocationContext
            );

            super.start(new TracingClientCallListener<>(responseListener, span), headers);
          }
        }

        @Override public void sendMessage(ReqT message) {
          try (Scope scope = currentTraceContext.maybeScope(span.context())) {
            super.sendMessage(message);
            parser.onMessageSent(message, span.customizer());
          }
        }
      };
    } catch (Throwable e) {
      error = e;
      throw e;
    } finally {
      if (error != null) span.error(error).finish();
    }
  }

  static final class TraceContextCallListener<RespT>
    extends SimpleForwardingClientCallListener<RespT> {
    final CurrentTraceContext currentTraceContext;
    @Nullable final TraceContext invocationContext;

    TraceContextCallListener(
      Listener<RespT> delegate,
      CurrentTraceContext currentTraceContext,
      @Nullable TraceContext invocationContext
    ) {
      super(delegate);
      this.currentTraceContext = currentTraceContext;
      this.invocationContext = invocationContext;
    }

    @Override public void onReady() {
      try (Scope scope = currentTraceContext.maybeScope(invocationContext)) {
        delegate().onReady();
      }
    }

    @Override public void onHeaders(Metadata headers) {
      try (Scope scope = currentTraceContext.maybeScope(invocationContext)) {
        delegate().onHeaders(headers);
      }
    }

    @Override public void onMessage(RespT message) {
      try (Scope scope = currentTraceContext.maybeScope(invocationContext)) {
        delegate().onMessage(message);
      }
    }

    @Override public void onClose(Status status, Metadata trailers) {
      try (Scope scope = currentTraceContext.maybeScope(invocationContext)) {
        delegate().onClose(status, trailers);
      }
    }
  }

  final class TracingClientCallListener<RespT> extends SimpleForwardingClientCallListener<RespT> {
    final Span span;

    TracingClientCallListener(Listener<RespT> responseListener, Span span) {
      super(responseListener);
      this.span = span;
    }

    @Override public void onMessage(RespT message) {
      try (Scope scope = currentTraceContext.maybeScope(span.context())) {
        parser.onMessageReceived(message, span.customizer());
        delegate().onMessage(message);
      }
    }

    @Override public void onClose(Status status, Metadata trailers) {
      try (Scope scope = currentTraceContext.maybeScope(span.context())) {
        super.onClose(status, trailers);
        parser.onClose(status, trailers, span.customizer());
      } finally {
        span.finish();
      }
    }
  }
}

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

import brave.NoopSpanCustomizer;
import brave.Span;
import brave.SpanCustomizer;
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
import io.grpc.Metadata.Key;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static brave.grpc.GrpcClientRequest.SETTER;

// not exposed directly as implementation notably changes between versions 1.2 and 1.3
final class TracingClientInterceptor implements ClientInterceptor {
  final Map<String, Key<String>> nameToKey;
  final CurrentTraceContext currentTraceContext;
  final Tracer tracer;
  final SamplerFunction<RpcRequest> sampler;
  final Injector<GrpcClientRequest> injector;
  final GrpcClientParser parser;
  final MessageProcessor messageProcessor;

  TracingClientInterceptor(GrpcTracing grpcTracing) {
    nameToKey = grpcTracing.nameToKey;
    currentTraceContext = grpcTracing.rpcTracing.tracing().currentTraceContext();
    tracer = grpcTracing.rpcTracing.tracing().tracer();
    sampler = grpcTracing.rpcTracing.clientSampler();
    injector = grpcTracing.propagation.injector(SETTER);
    parser = grpcTracing.clientParser;
    messageProcessor = grpcTracing.clientMessageProcessor;
  }

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
    CallOptions callOptions, Channel next) {
    return new TracingClientCall<>(
      method, callOptions, currentTraceContext.get(), next.newCall(method, callOptions));
  }

  void finish(GrpcClientResponse response, @Nullable Span span) {
    if (span == null || span.isNoop()) return;
    Throwable error = response.error();
    if (error != null) span.error(error);
    parser.onClose(response.status, response.trailers, span.customizer());
    span.finish();
  }

  void finishWithError(@Nullable Span span, Throwable error) {
    if (span == null || span.isNoop()) return;
    if (error != null) span.error(error);
    span.finish();
  }

  final class TracingClientCall<ReqT, RespT> extends SimpleForwardingClientCall<ReqT, RespT> {
    final MethodDescriptor<ReqT, RespT> method;
    final CallOptions callOptions;
    final TraceContext invocationContext;
    final AtomicReference<Span> spanRef = new AtomicReference<>();

    TracingClientCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions,
      TraceContext invocationContext, ClientCall<ReqT, RespT> call) {
      super(call);
      this.method = method;
      this.callOptions = callOptions;
      this.invocationContext = invocationContext;
    }

    @Override public void start(Listener<RespT> responseListener, Metadata headers) {
      GrpcClientRequest request =
        new GrpcClientRequest(nameToKey, method, callOptions, delegate(), headers);

      Span span = tracer.nextSpanWithParent(sampler, request, invocationContext);
      injector.inject(span.context(), request);
      if (!span.isNoop()) {
        span.kind(Span.Kind.CLIENT).start();
        parser.onStart(method, callOptions, headers, span.customizer());
      }
      spanRef.set(span);

      responseListener = new TracingClientCallListener<>(
        responseListener,
        invocationContext,
        spanRef,
        request
      );

      try (Scope scope = currentTraceContext.maybeScope(span.context())) {
        super.start(responseListener, headers);
      } catch (Throwable e) {
        // Another interceptor may throw an exception during start, in which case no other
        // callbacks are called, so go ahead and close the span here.
        finishWithError(spanRef.getAndSet(null), e);
        throw e;
      }
    }

    @Override public void cancel(@Nullable String message, @Nullable Throwable cause) {
      try (Scope scope = maybeScopeClientOrInvocationContext(spanRef, invocationContext)) {
        delegate().cancel(message, cause);
      }
    }

    @Override public void halfClose() {
      try (Scope scope = maybeScopeClientOrInvocationContext(spanRef, invocationContext)) {
        delegate().halfClose();
      } catch (Throwable e) {
        // If there was an exception executing onHalfClose, we don't expect other lifecycle
        // commands to succeed. Accordingly, we close the span
        finishWithError(spanRef.getAndSet(null), e);
        throw e;
      }
    }

    @Override public void request(int numMessages) {
      try (Scope scope = maybeScopeClientOrInvocationContext(spanRef, invocationContext)) {
        delegate().request(numMessages);
      }
    }

    @Override public void sendMessage(ReqT message) {
      try (Scope scope = maybeScopeClientOrInvocationContext(spanRef, invocationContext)) {
        delegate().sendMessage(message);
        Span span = spanRef.get(); // could be an error
        SpanCustomizer customizer = span != null ? span.customizer() : NoopSpanCustomizer.INSTANCE;
        messageProcessor.onMessageSent(message, customizer);
      }
    }
  }

  /** Scopes the client context or the invocation if the client span finished */
  Scope maybeScopeClientOrInvocationContext(
    AtomicReference<Span> spanRef,
    @Nullable TraceContext invocationContext
  ) {
    Span span = spanRef.get();
    TraceContext context = span != null ? span.context() : invocationContext;
    return currentTraceContext.maybeScope(context);
  }

  final class TracingClientCallListener<RespT> extends SimpleForwardingClientCallListener<RespT> {
    @Nullable final TraceContext invocationContext;
    final AtomicReference<Span> spanRef;
    final GrpcClientRequest request;

    TracingClientCallListener(
      Listener<RespT> delegate,
      @Nullable TraceContext invocationContext,
      AtomicReference<Span> spanRef,
      GrpcClientRequest request
    ) {
      super(delegate);
      this.invocationContext = invocationContext;
      this.spanRef = spanRef;
      this.request = request;
    }

    @Override public void onReady() {
      try (Scope scope = maybeScopeClientOrInvocationContext(spanRef, invocationContext)) {
        delegate().onReady();
      }
    }

    // See instrumentation/RATIONALE.md for why the below response callbacks are invocation context
    @Override public void onHeaders(Metadata headers) {
      try (Scope scope = currentTraceContext.maybeScope(invocationContext)) {
        delegate().onHeaders(headers);
      }
    }

    @Override public void onMessage(RespT message) {
      try (Scope scope = currentTraceContext.maybeScope(invocationContext)) {
        Span span = spanRef.get(); // could be an error
        SpanCustomizer customizer = span != null ? span.customizer() : NoopSpanCustomizer.INSTANCE;
        messageProcessor.onMessageReceived(message, customizer);
        delegate().onMessage(message);
      }
    }

    @Override public void onClose(Status status, Metadata trailers) {
      // See /instrumentation/grpc/RATIONALE.md for why we don't catch exceptions from the delegate
      GrpcClientResponse response = new GrpcClientResponse(request, status, trailers, null);
      finish(response, spanRef.getAndSet(null));

      try (Scope scope = currentTraceContext.maybeScope(invocationContext)) {
        delegate().onClose(status, trailers);
      }
    }
  }
}

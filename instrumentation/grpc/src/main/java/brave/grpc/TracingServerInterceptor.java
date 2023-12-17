/*
 * Copyright 2013-2023 The OpenZipkin Authors
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
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import brave.rpc.RpcServerHandler;
import io.grpc.ForwardingServerCall.SimpleForwardingServerCall;
import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static brave.internal.Throwables.propagateIfFatal;

// not exposed directly as implementation notably changes between versions 1.2 and 1.3
final class TracingServerInterceptor implements ServerInterceptor {
  final Map<String, Key<String>> nameToKey;
  final CurrentTraceContext currentTraceContext;
  final RpcServerHandler handler;
  final boolean grpcPropagationFormatEnabled;
  final MessageProcessor messageProcessor;

  TracingServerInterceptor(GrpcTracing grpcTracing) {
    nameToKey = grpcTracing.nameToKey;
    currentTraceContext = grpcTracing.rpcTracing.tracing().currentTraceContext();
    handler = RpcServerHandler.create(grpcTracing.rpcTracing);
    grpcPropagationFormatEnabled = grpcTracing.grpcPropagationFormatEnabled;
    messageProcessor = grpcTracing.serverMessageProcessor;
  }

  @Override
  public <ReqT, RespT> Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
    Metadata headers, ServerCallHandler<ReqT, RespT> next) {
    GrpcServerRequest request = new GrpcServerRequest(nameToKey, call, headers);

    Span span = handler.handleReceive(request);
    AtomicReference<Span> spanRef = new AtomicReference<Span>(span);

    // startCall invokes user interceptors, so we place the span in scope here
    Listener<ReqT> result;
    Throwable error = null;
    Scope scope = currentTraceContext.maybeScope(span.context());
    try {
      result =
        next.startCall(new TracingServerCall<ReqT, RespT>(call, span, spanRef, request), headers);
    } catch (RuntimeException e) {
      error = e;
      throw e;
    } catch (Error e) {
      propagateIfFatal(e);
      error = e;
      throw e;
    } finally {
      if (error != null) {
        // Another interceptor may throw an exception during startCall, in which case no other
        // callbacks are called, so go ahead and close the span here.
        //
        // See instrumentation/grpc/RATIONALE.md for why we don't use the handler here
        spanRef.set(null);
        if (span != null) span.error(error).finish();
      }
      scope.close();
    }

    return new TracingServerCallListener<ReqT>(result, span, spanRef, request);
  }

  final class TracingServerCall<ReqT, RespT> extends SimpleForwardingServerCall<ReqT, RespT> {
    final TraceContext context;
    final AtomicReference<Span> spanRef;
    final GrpcServerRequest request;
    final Metadata headers = new Metadata();

    TracingServerCall(ServerCall<ReqT, RespT> delegate, Span span, AtomicReference<Span> spanRef,
      GrpcServerRequest request) {
      super(delegate);
      this.context = span.context();
      this.spanRef = spanRef;
      this.request = request;
    }

    @Override public void request(int numMessages) {
      Scope scope = currentTraceContext.maybeScope(context);
      try {
        delegate().request(numMessages);
      } finally {
        scope.close();
      }
    }

    @Override public void sendHeaders(Metadata headers) {
      Scope scope = currentTraceContext.maybeScope(context);
      try {
        delegate().sendHeaders(headers);
      } finally {
        scope.close();
      }
      // sendHeaders() JavaDoc mentions headers are not thread-safe, so we make a safe copy here.
      this.headers.merge(headers);
    }

    @Override public void sendMessage(RespT message) {
      Scope scope = currentTraceContext.maybeScope(context);
      try {
        delegate().sendMessage(message);
        Span span = spanRef.get(); // could be an error
        SpanCustomizer customizer = span != null ? span.customizer() : NoopSpanCustomizer.INSTANCE;
        messageProcessor.onMessageSent(message, customizer);
      } finally {
        scope.close();
      }
    }

    @Override public void close(Status status, Metadata trailers) {
      // See /instrumentation/grpc/RATIONALE.md for why we don't catch exceptions from the delegate
      GrpcServerResponse response = new GrpcServerResponse(request, headers, status, trailers);
      Span span = spanRef.getAndSet(null);
      if (span != null) handler.handleSend(response, span);

      Scope scope = currentTraceContext.maybeScope(context);
      try {
        delegate().close(status, trailers);
      } finally {
        scope.close();
      }
    }
  }

  final class TracingServerCallListener<RespT> extends SimpleForwardingServerCallListener<RespT> {
    final TraceContext context;
    final AtomicReference<Span> spanRef;
    final GrpcServerRequest request;

    TracingServerCallListener(
      Listener<RespT> delegate,
      Span span,
      AtomicReference<Span> spanRef,
      GrpcServerRequest request
    ) {
      super(delegate);
      this.context = span.context();
      this.spanRef = spanRef;
      this.request = request;
    }

    @Override public void onMessage(RespT message) {
      Scope scope = currentTraceContext.maybeScope(context);
      try {
        delegate().onMessage(message);
        Span span = spanRef.get(); // could be an error
        SpanCustomizer customizer = span != null ? span.customizer() : NoopSpanCustomizer.INSTANCE;
        messageProcessor.onMessageReceived(message, customizer);
      } finally {
        scope.close();
      }
    }

    @Override public void onHalfClose() {
      Scope scope = currentTraceContext.maybeScope(context);
      Throwable error = null;
      try {
        delegate().onHalfClose();
      } catch (RuntimeException e) {
        error = e;
        throw e;
      } catch (Error e) {
        propagateIfFatal(e);
        error = e;
        throw e;
      } finally {
        if (error != null) {
          // If there was an exception executing onHalfClose, we don't expect other lifecycle
          // commands to succeed. Accordingly, we close the span
          //
          // See instrumentation/grpc/RATIONALE.md for why we don't use the handler here
          Span span = spanRef.getAndSet(null);
          if (span != null) span.error(error).finish();
        }
        scope.close();
      }
    }

    @Override public void onCancel() {
      Scope scope = currentTraceContext.maybeScope(context);
      try {
        delegate().onCancel();
      } finally {
        scope.close();
      }
    }

    @Override public void onComplete() {
      Scope scope = currentTraceContext.maybeScope(context);
      try {
        delegate().onComplete();
      } finally {
        scope.close();
      }
    }

    @Override public void onReady() {
      Scope scope = currentTraceContext.maybeScope(context);
      try {
        delegate().onReady();
      } finally {
        scope.close();
      }
    }
  }
}

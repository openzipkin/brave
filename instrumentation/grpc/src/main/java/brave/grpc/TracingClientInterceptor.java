/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.grpc;

import brave.Span;
import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import brave.rpc.RpcClientHandler;
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

import static brave.internal.Throwables.propagateIfFatal;

// not exposed directly as implementation notably changes between versions 1.2 and 1.3
final class TracingClientInterceptor implements ClientInterceptor {
  final Map<String, Key<String>> nameToKey;
  final CurrentTraceContext currentTraceContext;
  final RpcClientHandler handler;

  TracingClientInterceptor(GrpcTracing grpcTracing) {
    nameToKey = grpcTracing.nameToKey;
    currentTraceContext = grpcTracing.rpcTracing.tracing().currentTraceContext();
    handler = RpcClientHandler.create(grpcTracing.rpcTracing);
  }

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
    CallOptions callOptions, Channel next) {
    return new TracingClientCall<ReqT, RespT>(
      method, callOptions, currentTraceContext.get(), next.newCall(method, callOptions));
  }

  final class TracingClientCall<ReqT, RespT> extends SimpleForwardingClientCall<ReqT, RespT> {
    final MethodDescriptor<ReqT, RespT> method;
    final CallOptions callOptions;
    final TraceContext invocationContext;
    final AtomicReference<Span> spanRef = new AtomicReference<Span>();

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

      Span span = handler.handleSendWithParent(request, invocationContext);
      spanRef.set(span);

      responseListener = new TracingClientCallListener<RespT>(
        responseListener,
        invocationContext,
        spanRef,
        request
      );

      Scope scope = currentTraceContext.maybeScope(span.context());
      Throwable error = null;
      try {
        super.start(responseListener, headers);
      } catch (RuntimeException e) {
        error = e;
        throw e;
      } catch (Error e) {
        propagateIfFatal(e);
        error = e;
        throw e;
      } finally {
        if (error != null) {
          // Another interceptor may throw an exception during start, in which case no other
          // callbacks are called, so go ahead and close the span here.
          //
          // See instrumentation/grpc/RATIONALE.md for why we don't use the handler here
          spanRef.set(null);
          if (span != null) span.error(error).finish();
        }
        scope.close();
      }
    }

    @Override public void cancel(@Nullable String message, @Nullable Throwable cause) {
      Scope scope = maybeScopeClientOrInvocationContext(spanRef, invocationContext);
      try {
        delegate().cancel(message, cause);
      } finally {
        scope.close();
      }
    }

    @Override public void halfClose() {
      Scope scope = maybeScopeClientOrInvocationContext(spanRef, invocationContext);
      Throwable error = null;
      try {
        delegate().halfClose();
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

    @Override public void request(int numMessages) {
      Scope scope = maybeScopeClientOrInvocationContext(spanRef, invocationContext);
      try {
        delegate().request(numMessages);
      } finally {
        scope.close();
      }
    }

    @Override public void sendMessage(ReqT message) {
      Scope scope = maybeScopeClientOrInvocationContext(spanRef, invocationContext);
      try {
        delegate().sendMessage(message);
      } finally {
        scope.close();
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
    final Metadata headers = new Metadata();

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
      Scope scope = maybeScopeClientOrInvocationContext(spanRef, invocationContext);
      try {
        delegate().onReady();
      } finally {
        scope.close();
      }
    }

    // See instrumentation/RATIONALE.md for why the below response callbacks are invocation context
    @Override public void onHeaders(Metadata headers) {
      // onHeaders() JavaDoc mentions headers are not thread-safe, so we make a safe copy here.
      this.headers.merge(headers);
      Scope scope = currentTraceContext.maybeScope(invocationContext);
      try {
        delegate().onHeaders(headers);
      } finally {
        scope.close();
      }
    }

    @Override public void onMessage(RespT message) {
      Scope scope = currentTraceContext.maybeScope(invocationContext);
      try {
        delegate().onMessage(message);
      } finally {
        scope.close();
      }
    }

    @Override public void onClose(Status status, Metadata trailers) {
      // See /instrumentation/grpc/RATIONALE.md for why we don't catch exceptions from the delegate
      GrpcClientResponse response = new GrpcClientResponse(request, headers, status, trailers);
      Span span = spanRef.getAndSet(null);
      if (span != null) handler.handleReceive(response, span);

      Scope scope = currentTraceContext.maybeScope(invocationContext);
      try {
        delegate().onClose(status, trailers);
      } finally {
        scope.close();
      }
    }
  }
}

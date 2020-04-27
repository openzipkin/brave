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
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.rpc.RpcRequest;
import brave.sampler.SamplerFunction;
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

import static brave.grpc.GrpcServerRequest.GETTER;

// not exposed directly as implementation notably changes between versions 1.2 and 1.3
final class TracingServerInterceptor implements ServerInterceptor {
  final Map<String, Key<String>> nameToKey;
  final CurrentTraceContext currentTraceContext;
  final Tracer tracer;
  final Extractor<GrpcServerRequest> extractor;
  final SamplerFunction<RpcRequest> sampler;
  final GrpcServerParser parser;
  final boolean grpcPropagationFormatEnabled;
  final MessageProcessor messageProcessor;

  TracingServerInterceptor(GrpcTracing grpcTracing) {
    nameToKey = grpcTracing.nameToKey;
    currentTraceContext = grpcTracing.rpcTracing.tracing().currentTraceContext();
    tracer = grpcTracing.rpcTracing.tracing().tracer();
    extractor = grpcTracing.propagation.extractor(GETTER);
    sampler = grpcTracing.rpcTracing.serverSampler();
    parser = grpcTracing.serverParser;
    grpcPropagationFormatEnabled = grpcTracing.grpcPropagationFormatEnabled;
    messageProcessor = grpcTracing.serverMessageProcessor;
  }

  @Override
  public <ReqT, RespT> Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
    Metadata headers, ServerCallHandler<ReqT, RespT> next) {
    GrpcServerRequest request = new GrpcServerRequest(nameToKey, call, headers);

    Span span = nextSpan(extractor.extract(request), request);
    if (!span.isNoop()) {
      span.kind(Span.Kind.SERVER).start();
      parser.onStart(call, headers, span.customizer());
    }
    AtomicReference<Span> spanRef = new AtomicReference<>(span);

    // startCall invokes user interceptors, so we place the span in scope here
    Listener<ReqT> result;
    try (Scope scope = currentTraceContext.maybeScope(span.context())) {
      result = next.startCall(new TracingServerCall<>(call, span, spanRef, request), headers);
    } catch (Throwable e) {
      // Another interceptor may throw an exception during startCall, in which case no other
      // callbacks are called, so go ahead and close the span here.
      finishWithError(spanRef.getAndSet(null), e);
      throw e;
    }

    return new TracingServerCallListener<>(result, span, spanRef, request);
  }

  /** Creates a potentially noop span representing this request */
  // This is the same code as HttpServerHandler.nextSpan
  // TODO: pull this into RpcServerHandler when stable https://github.com/openzipkin/brave/pull/999
  Span nextSpan(TraceContextOrSamplingFlags extracted, GrpcServerRequest request) {
    Boolean sampled = extracted.sampled();
    // only recreate the context if the sampler made a decision
    if (sampled == null && (sampled = sampler.trySample(request)) != null) {
      extracted = extracted.sampled(sampled.booleanValue());
    }
    return extracted.context() != null
      ? tracer.joinSpan(extracted.context())
      : tracer.nextSpan(extracted);
  }

  void finish(GrpcServerResponse response, @Nullable Span span) {
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

  final class TracingServerCall<ReqT, RespT> extends SimpleForwardingServerCall<ReqT, RespT> {
    final TraceContext context;
    final AtomicReference<Span> spanRef;
    final GrpcServerRequest request;

    TracingServerCall(ServerCall<ReqT, RespT> delegate, Span span, AtomicReference<Span> spanRef,
      GrpcServerRequest request) {
      super(delegate);
      this.context = span.context();
      this.spanRef = spanRef;
      this.request = request;
    }

    @Override public void request(int numMessages) {
      try (Scope scope = currentTraceContext.maybeScope(context)) {
        delegate().request(numMessages);
      }
    }

    @Override public void sendHeaders(Metadata headers) {
      try (Scope scope = currentTraceContext.maybeScope(context)) {
        delegate().sendHeaders(headers);
      }
    }

    @Override public void sendMessage(RespT message) {
      try (Scope scope = currentTraceContext.maybeScope(context)) {
        delegate().sendMessage(message);
        Span span = spanRef.get(); // could be an error
        SpanCustomizer customizer = span != null ? span.customizer() : NoopSpanCustomizer.INSTANCE;
        messageProcessor.onMessageSent(message, customizer);
      }
    }

    @Override public void close(Status status, Metadata trailers) {
      // See /instrumentation/grpc/RATIONALE.md for why we don't catch exceptions from the delegate
      GrpcServerResponse response = new GrpcServerResponse(request, status, trailers, null);
      finish(response, spanRef.getAndSet(null));

      try (Scope scope = currentTraceContext.maybeScope(context)) {
        delegate().close(status, trailers);
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
      try (Scope scope = currentTraceContext.maybeScope(context)) {
        delegate().onMessage(message);
        Span span = spanRef.get(); // could be an error
        SpanCustomizer customizer = span != null ? span.customizer() : NoopSpanCustomizer.INSTANCE;
        messageProcessor.onMessageReceived(message, customizer);
      }
    }

    @Override public void onHalfClose() {
      try (Scope scope = currentTraceContext.maybeScope(context)) {
        delegate().onHalfClose();
      } catch (Throwable e) {
        // If there was an exception executing onHalfClose, we don't expect other lifecycle
        // commands to succeed. Accordingly, we close the span
        finishWithError(spanRef.getAndSet(null), e);
        throw e;
      }
    }

    @Override public void onCancel() {
      try (Scope scope = currentTraceContext.maybeScope(context)) {
        delegate().onCancel();
      }
    }

    @Override public void onComplete() {
      try (Scope scope = currentTraceContext.maybeScope(context)) {
        delegate().onComplete();
      }
    }

    @Override public void onReady() {
      try (Scope scope = currentTraceContext.maybeScope(context)) {
        delegate().onReady();
      }
    }
  }
}

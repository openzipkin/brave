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
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.rpc.RpcRequest;
import brave.sampler.SamplerFunction;
import io.grpc.ForwardingServerCall.SimpleForwardingServerCall;
import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import java.util.Map;

import static brave.grpc.GrpcServerRequest.GETTER;

// not exposed directly as implementation notably changes between versions 1.2 and 1.3
final class TracingServerInterceptor implements ServerInterceptor {
  final Tracer tracer;
  final CurrentTraceContext currentTraceContext;
  final Extractor<GrpcServerRequest> extractor;
  final SamplerFunction<RpcRequest> sampler;
  final GrpcServerParser parser;
  final Map<String, Metadata.Key<String>> nameToKey;
  final boolean grpcPropagationFormatEnabled;

  TracingServerInterceptor(GrpcTracing grpcTracing) {
    tracer = grpcTracing.rpcTracing.tracing().tracer();
    currentTraceContext = grpcTracing.rpcTracing.tracing().currentTraceContext();
    extractor = grpcTracing.propagation.extractor(GETTER);
    sampler = grpcTracing.rpcTracing.serverSampler();
    parser = grpcTracing.serverParser;
    nameToKey = grpcTracing.nameToKey;
    grpcPropagationFormatEnabled = grpcTracing.grpcPropagationFormatEnabled;
  }

  @Override
  public <ReqT, RespT> Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
    Metadata headers, ServerCallHandler<ReqT, RespT> next) {
    GrpcServerRequest request =
      new GrpcServerRequest(nameToKey, call.getMethodDescriptor(), headers);
    TraceContextOrSamplingFlags extracted = extractor.extract(request);
    Span span = nextSpan(extracted, request).kind(Span.Kind.SERVER);
    parser.onStart(call, headers, span.customizer());
    // startCall invokes user interceptors, so we place the span in scope here
    Listener<ReqT> result;
    Throwable error = null;
    try (Scope scope = currentTraceContext.maybeScope(span.context())) {
      result = next.startCall(new TracingServerCall<>(span, call), headers);
    } catch (Throwable e) {
      error = e;
      throw e;
    } finally {
      if (error != null) span.error(error).finish();
    }

    // This ensures the server implementation can see the span in scope
    return new TracingServerCallListener<>(result, currentTraceContext, parser, span);
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

  final class TracingServerCall<ReqT, RespT> extends SimpleForwardingServerCall<ReqT, RespT> {
    final Span span;

    TracingServerCall(Span span, ServerCall<ReqT, RespT> call) {
      super(call);
      this.span = span;
    }

    @Override public void request(int numMessages) {
      span.start();
      try (Scope scope = currentTraceContext.maybeScope(span.context())) {
        super.request(numMessages);
      }
    }

    @Override public void sendHeaders(Metadata headers) {
      try (Scope scope = currentTraceContext.maybeScope(span.context())) {
        super.sendHeaders(headers);
      }
    }

    @Override public void sendMessage(RespT message) {
      try (Scope scope = currentTraceContext.maybeScope(span.context())) {
        super.sendMessage(message);
        parser.onMessageSent(message, span.customizer());
      }
    }

    @Override public void close(Status status, Metadata trailers) {
      try (Scope scope = currentTraceContext.maybeScope(span.context())) {
        super.close(status, trailers);
        parser.onClose(status, trailers, span.customizer());
      } catch (Throwable e) {
        span.error(e);
        throw e;
      } finally {
        span.finish();
      }
    }
  }

  static final class TracingServerCallListener<ReqT>
    extends SimpleForwardingServerCallListener<ReqT> {
    final CurrentTraceContext currentTraceContext;
    final Span span;
    final GrpcServerParser parser;

    TracingServerCallListener(Listener<ReqT> delegate, CurrentTraceContext currentTraceContext,
      GrpcServerParser parser, Span span) {
      super(delegate);
      this.currentTraceContext = currentTraceContext;
      this.span = span;
      this.parser = parser;
    }

    @Override public void onMessage(ReqT message) {
      try (Scope scope = currentTraceContext.maybeScope(span.context())) {
        parser.onMessageReceived(message, span.customizer());
        delegate().onMessage(message);
      }
    }

    @Override public void onHalfClose() {
      Throwable error = null;
      try (Scope scope = currentTraceContext.maybeScope(span.context())) {
        delegate().onHalfClose();
      } catch (Throwable e) {
        error = e;
        throw e;
      } finally {
        // If there was an exception executing onHalfClose, we don't expect other lifecycle
        // commands to succeed. Accordingly, we close the span
        if (error != null) span.error(error).finish();
      }
    }

    @Override public void onCancel() {
      try (Scope scope = currentTraceContext.maybeScope(span.context())) {
        delegate().onCancel();
      }
    }

    @Override public void onComplete() {
      try (Scope scope = currentTraceContext.maybeScope(span.context())) {
        delegate().onComplete();
      }
    }

    @Override public void onReady() {
      try (Scope scope = currentTraceContext.maybeScope(span.context())) {
        delegate().onReady();
      }
    }
  }
}

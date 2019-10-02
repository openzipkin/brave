/*
 * Copyright 2013-2019 The OpenZipkin Authors
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
import brave.Tracer.SpanInScope;
import brave.grpc.GrpcPropagation.Tags;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.rpc.RpcRequest;
import brave.sampler.SamplerFunction;
import io.grpc.ForwardingServerCall.SimpleForwardingServerCall;
import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

import static brave.grpc.GrpcPropagation.RPC_METHOD;
import static brave.grpc.GrpcServerRequest.GETTER;

// not exposed directly as implementation notably changes between versions 1.2 and 1.3
final class TracingServerInterceptor implements ServerInterceptor {
  final Tracer tracer;
  final Extractor<GrpcServerRequest> extractor;
  final SamplerFunction<RpcRequest> sampler;
  final GrpcServerParser parser;
  final boolean grpcPropagationFormatEnabled;

  TracingServerInterceptor(GrpcTracing grpcTracing) {
    tracer = grpcTracing.rpcTracing.tracing().tracer();
    extractor = grpcTracing.propagation.extractor(GETTER);
    sampler = grpcTracing.rpcTracing.serverSampler();
    parser = grpcTracing.serverParser;
    grpcPropagationFormatEnabled = grpcTracing.grpcPropagationFormatEnabled;
  }

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
    Metadata headers, ServerCallHandler<ReqT, RespT> next) {
    GrpcServerRequest request = new GrpcServerRequest(call.getMethodDescriptor(), headers);
    TraceContextOrSamplingFlags extracted = extractor.extract(request);
    Span span = nextSpan(extracted, request);

    // If grpc propagation is enabled, make sure we refresh the server method
    if (grpcPropagationFormatEnabled) {
      Tags tags = span.context().findExtra(Tags.class);
      if (tags != null) tags.put(RPC_METHOD, call.getMethodDescriptor().getFullMethodName());
    }

    span.kind(Span.Kind.SERVER);
    parser.onStart(call, headers, span.customizer());
    // startCall invokes user interceptors, so we place the span in scope here
    ServerCall.Listener<ReqT> result;
    SpanInScope scope = tracer.withSpanInScope(span);
    try { // retrolambda can't resolve this try/finally
      result = next.startCall(new TracingServerCall<>(span, call), headers);
    } catch (RuntimeException | Error e) {
      span.error(e);
      span.finish();
      throw e;
    } finally {
      scope.close();
    }

    // This ensures the server implementation can see the span in scope
    return new ScopingServerCallListener<>(tracer, span, result, parser);
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
      super.request(numMessages);
    }

    @Override
    public void sendMessage(RespT message) {
      super.sendMessage(message);
      parser.onMessageSent(message, span.customizer());
    }

    @Override public void close(Status status, Metadata trailers) {
      try {
        super.close(status, trailers);
        parser.onClose(status, trailers, span.customizer());
      } catch (RuntimeException | Error e) {
        span.error(e);
        throw e;
      } finally {
        span.finish();
      }
    }
  }

  static final class ScopingServerCallListener<ReqT>
    extends SimpleForwardingServerCallListener<ReqT> {
    final Tracer tracer;
    final Span span;
    final GrpcServerParser parser;

    ScopingServerCallListener(Tracer tracer, Span span, ServerCall.Listener<ReqT> delegate,
      GrpcServerParser parser) {
      super(delegate);
      this.tracer = tracer;
      this.span = span;
      this.parser = parser;
    }

    @Override public void onMessage(ReqT message) {
      SpanInScope scope = tracer.withSpanInScope(span);
      try { // retrolambda can't resolve this try/finally
        parser.onMessageReceived(message, span.customizer());
        delegate().onMessage(message);
      } finally {
        scope.close();
      }
    }

    @Override public void onHalfClose() {
      SpanInScope scope = tracer.withSpanInScope(span);
      try { // retrolambda can't resolve this try/finally
        delegate().onHalfClose();
      } catch (RuntimeException | Error e) {
        // If there was an exception executing onHalfClose, we don't expect other lifecycle
        // commands to succeed. Accordingly, we close the span
        span.error(e);
        span.finish();
        throw e;
      } finally {
        scope.close();
      }
    }

    @Override public void onCancel() {
      SpanInScope scope = tracer.withSpanInScope(span);
      try { // retrolambda can't resolve this try/finally
        delegate().onCancel();
      } finally {
        scope.close();
      }
    }

    @Override public void onComplete() {
      SpanInScope scope = tracer.withSpanInScope(span);
      try { // retrolambda can't resolve this try/finally
        delegate().onComplete();
      } finally {
        scope.close();
      }
    }

    @Override public void onReady() {
      SpanInScope scope = tracer.withSpanInScope(span);
      try { // retrolambda can't resolve this try/finally
        delegate().onReady();
      } finally {
        scope.close();
      }
    }
  }
}

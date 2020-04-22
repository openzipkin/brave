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
package brave.dubbo.rpc;

import brave.Span;
import brave.Span.Kind;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.rpc.RpcRequest;
import brave.rpc.RpcTracing;
import brave.sampler.SamplerFunction;
import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.extension.Activate;
import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.config.spring.extension.SpringExtensionFactory;
import com.alibaba.dubbo.remoting.RemotingException;
import com.alibaba.dubbo.remoting.exchange.ResponseCallback;
import com.alibaba.dubbo.remoting.exchange.ResponseFuture;
import com.alibaba.dubbo.rpc.Filter;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcContext;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.protocol.dubbo.FutureAdapter;
import java.util.Map;
import java.util.concurrent.Future;

import static brave.dubbo.rpc.DubboClientRequest.SETTER;
import static brave.dubbo.rpc.DubboServerRequest.GETTER;
import static brave.sampler.SamplerFunctions.deferDecision;

@Activate(group = {Constants.PROVIDER, Constants.CONSUMER}, value = "tracing")
// http://dubbo.apache.org/en-us/docs/dev/impls/filter.html
// public constructor permitted to allow dubbo to instantiate this
public final class TracingFilter implements Filter {

  CurrentTraceContext currentTraceContext;
  Tracer tracer;
  Extractor<DubboServerRequest> extractor;
  Injector<DubboClientRequest> injector;
  SamplerFunction<RpcRequest> clientSampler = deferDecision(), serverSampler = deferDecision();
  volatile boolean isInit = false;

  /**
   * {@link ExtensionLoader} supplies the tracing implementation which must be named "tracing". For
   * example, if using the {@link SpringExtensionFactory}, only a bean named "tracing" will be
   * injected.
   */
  public void setTracing(Tracing tracing) {
    if (tracing == null) throw new NullPointerException("rpcTracing == null");
    if (isInit) return; // don't override an existing Rpc Tracing
    setRpcTracing(RpcTracing.create(tracing));
  }

  /**
   * {@link ExtensionLoader} supplies the tracing implementation which must be named "rpcTracing".
   * For example, if using the {@link SpringExtensionFactory}, only a bean named "rpcTracing" will
   * be injected.
   */
  public void setRpcTracing(RpcTracing rpcTracing) {
    if (rpcTracing == null) throw new NullPointerException("rpcTracing == null");
    // we don't guard on init because we intentionally want to overwrite any call to setTracing
    currentTraceContext = rpcTracing.tracing().currentTraceContext();
    extractor = rpcTracing.tracing().propagation().extractor(GETTER);
    injector = rpcTracing.tracing().propagation().injector(SETTER);
    tracer = rpcTracing.tracing().tracer();
    clientSampler = rpcTracing.clientSampler();
    serverSampler = rpcTracing.serverSampler();
    isInit = true;
  }

  @Override public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
    if (!isInit) return invoker.invoke(invocation);

    RpcContext rpcContext = RpcContext.getContext();
    Kind kind = rpcContext.isProviderSide() ? Kind.SERVER : Kind.CLIENT;
    final Span span;
    if (kind.equals(Kind.CLIENT)) {
      // When A service invoke B service, then B service then invoke C service, the parentId of the
      // C service span is A when read from invocation.getAttachments(). This is because
      // AbstractInvoker adds attachments via RpcContext.getContext(), not the invocation.
      // See com.alibaba.dubbo.rpc.protocol.AbstractInvoker(line 138) from v2.6.7
      Map<String, String> attachments = RpcContext.getContext().getAttachments();
      DubboClientRequest request = new DubboClientRequest(invocation, attachments);
      span = tracer.nextSpan(clientSampler, request);
      injector.inject(span.context(), request);
    } else {
      DubboServerRequest request = new DubboServerRequest(invocation, invocation.getAttachments());
      TraceContextOrSamplingFlags extracted = extractor.extract(request);
      span = nextSpan(extracted, request);
    }
    if (!span.isNoop()) {
      span.kind(kind);
      String service = DubboParser.service(invocation);
      String method = DubboParser.method(invocation);
      span.name(service + "/" + method);
      DubboParser.parseRemoteIpAndPort(span);
      span.start();
    }

    boolean deferFinish = false;
    Scope scope = currentTraceContext.newScope(span.context());
    Throwable error = null;
    try {
      Result result = invoker.invoke(invocation);
      if (!span.isNoop()) {
        error = result.getException();
        deferFinish = ensureSpanFinishes(rpcContext, span);
      }
      return result;
    } catch (Throwable e) {
      error = e;
      throw e;
    } finally {
      if (error != null) onError(error, span);
      if (!deferFinish) span.finish();
      scope.close();
    }
  }

  /** Creates a potentially noop span representing this request */
  // This is the same code as HttpServerHandler.nextSpan
  // TODO: pull this into RpcServerHandler when stable https://github.com/openzipkin/brave/pull/999
  Span nextSpan(TraceContextOrSamplingFlags extracted, DubboServerRequest request) {
    Boolean sampled = extracted.sampled();
    // only recreate the context if the sampler made a decision
    if (sampled == null && (sampled = serverSampler.trySample(request)) != null) {
      extracted = extracted.sampled(sampled.booleanValue());
    }
    return extracted.context() != null
      ? tracer.joinSpan(extracted.context())
      : tracer.nextSpan(extracted);
  }

  boolean ensureSpanFinishes(RpcContext rpcContext, Span span) {
    boolean deferFinish = false;
    Future<Object> future = rpcContext.getFuture(); // the case on async client invocation
    if (future instanceof FutureAdapter) {
      deferFinish = true;
      ResponseFuture original = ((FutureAdapter<Object>) future).getFuture();
      ResponseFuture wrapped = new FinishSpanResponseFuture(original, span, currentTraceContext);
      // Ensures even if no callback added later, for example when a consumer, we finish the span
      wrapped.setCallback(null);
      RpcContext.getContext().setFuture(new FutureAdapter<>(wrapped));
    }
    return deferFinish;
  }

  static void onError(Throwable error, Span span) {
    span.error(error);
    String errorCode = DubboParser.errorCode(error);
    if (errorCode != null) span.tag("dubbo.error_code", errorCode);
  }

  /** Ensures any callbacks finish the span. */
  static final class FinishSpanResponseFuture implements ResponseFuture {
    final ResponseFuture delegate;
    final Span span;
    final CurrentTraceContext currentTraceContext;

    FinishSpanResponseFuture(ResponseFuture delegate, Span span,
      CurrentTraceContext currentTraceContext) {
      this.delegate = delegate;
      this.span = span;
      this.currentTraceContext = currentTraceContext;
    }

    @Override public Object get() throws RemotingException {
      return delegate.get();
    }

    @Override public Object get(int timeoutInMillis) throws RemotingException {
      return delegate.get(timeoutInMillis);
    }

    @Override public void setCallback(ResponseCallback callback) {
      delegate.setCallback(TracingResponseCallback.create(callback, span, currentTraceContext));
    }

    @Override public boolean isDone() {
      return delegate.isDone();
    }
  }
}

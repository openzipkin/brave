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
package brave.dubbo;

import brave.Span;
import brave.Span.Kind;
import brave.Tracer;
import brave.Tracing;
import brave.internal.Platform;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.rpc.RpcRequest;
import brave.rpc.RpcTracing;
import brave.sampler.SamplerFunction;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.Future;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.config.spring.extension.SpringExtensionFactory;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.protocol.dubbo.FutureAdapter;
import org.apache.dubbo.rpc.support.RpcUtils;

import static brave.dubbo.DubboClientRequest.SETTER;
import static brave.dubbo.DubboServerRequest.GETTER;
import static brave.sampler.SamplerFunctions.deferDecision;

@Activate(group = {CommonConstants.PROVIDER, CommonConstants.CONSUMER}, value = "tracing")
// http://dubbo.apache.org/en-us/docs/dev/impls/filter.html
// public constructor permitted to allow dubbo to instantiate this
public final class TracingFilter implements Filter {

  Tracer tracer;
  TraceContext.Extractor<DubboServerRequest> extractor;
  TraceContext.Injector<DubboClientRequest> injector;
  SamplerFunction<RpcRequest> clientSampler = deferDecision(), serverSampler = deferDecision();
  volatile boolean isInit = false;

  /**
   * {@link ExtensionLoader} supplies the tracing implementation which must be named "tracing". For
   * example, if using the {@link SpringExtensionFactory}, only a bean named "tracing" will be
   * injected.
   */
  public void setTracing(Tracing tracing) {
    if (tracing == null) throw new NullPointerException("rpcTracing == null");
    tracer = tracing.tracer();
    extractor = tracing.propagation().extractor(GETTER);
    injector = tracing.propagation().injector(SETTER);
    isInit = true;
  }

  /**
   * {@link ExtensionLoader} supplies the tracing implementation which must be named "rpcTracing".
   * For example, if using the {@link SpringExtensionFactory}, only a bean named "rpcTracing" will
   * be injected.
   */
  public void setRpcTracing(RpcTracing rpcTracing) {
    if (rpcTracing == null) throw new NullPointerException("rpcTracing == null");
    tracer = rpcTracing.tracing().tracer();
    extractor = rpcTracing.tracing().propagation().extractor(GETTER);
    injector = rpcTracing.tracing().propagation().injector(SETTER);
    clientSampler = rpcTracing.clientSampler();
    serverSampler = rpcTracing.serverSampler();
    isInit = true;
  }

  @Override
  public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
    if (!isInit) return invoker.invoke(invocation);

    RpcContext rpcContext = RpcContext.getContext();
    Kind kind = rpcContext.isProviderSide() ? Kind.SERVER : Kind.CLIENT;
    final Span span;
    if (kind.equals(Kind.CLIENT)) {
      // When A service invoke B service, then B service then invoke C service, the parentId of the
      // C service span is A when read from invocation.getAttachments(). This is because
      // AbstractInvoker adds attachments via RpcContext.getContext(), not the invocation.
      // See org.apache.dubbo.rpc.protocol.AbstractInvoker(line 141) from v2.7.3
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
      String service = invoker.getInterface().getSimpleName();
      String method = RpcUtils.getMethodName(invocation);
      span.name(service + "/" + method);
      parseRemoteAddress(rpcContext, span);
      span.start();
    }

    boolean isOneway = false, deferFinish = false;
    try (Tracer.SpanInScope scope = tracer.withSpanInScope(span)) {
      Result result = invoker.invoke(invocation);
      if (result.hasException()) {
        onError(result.getException(), span);
      }
      isOneway = RpcUtils.isOneway(invoker.getUrl(), invocation);
      Future<Object> future = rpcContext.getFuture(); // the case on async client invocation
      if (!isOneway && future instanceof FutureAdapter) {
        deferFinish = true;
        ((FutureAdapter<Object>) future).whenComplete((v, t) -> {
          if (t != null) {
            onError(t, span);
            span.finish();
          } else {
            span.finish();
          }
        });
      }
      return result;
    } catch (Error | RuntimeException e) {
      onError(e, span);
      throw e;
    } finally {
      if (isOneway) {
        span.flush();
      } else if (!deferFinish) {
        span.finish();
      }
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

  static void parseRemoteAddress(RpcContext rpcContext, Span span) {
    InetSocketAddress remoteAddress = rpcContext.getRemoteAddress();
    if (remoteAddress == null) return;
    span.remoteIpAndPort(Platform.get().getHostString(remoteAddress), remoteAddress.getPort());
  }

  static void onError(Throwable error, Span span) {
    span.error(error);
    if (error instanceof RpcException) {
      span.tag("dubbo.error_code", Integer.toString(((RpcException) error).getCode()));
    }
  }
}

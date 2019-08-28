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
package brave.dubbo.rpc;

import brave.Span;
import brave.Span.Kind;
import brave.Tracer;
import brave.Tracing;
import brave.internal.Platform;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.extension.Activate;
import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.config.spring.extension.SpringExtensionFactory;
import com.alibaba.dubbo.remoting.exchange.ResponseCallback;
import com.alibaba.dubbo.rpc.Filter;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcContext;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.protocol.dubbo.FutureAdapter;
import com.alibaba.dubbo.rpc.support.RpcUtils;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.Future;

@Activate(group = {Constants.PROVIDER, Constants.CONSUMER}, value = "tracing")
// http://dubbo.apache.org/en-us/docs/dev/impls/filter.html
// public constructor permitted to allow dubbo to instantiate this
public final class TracingFilter implements Filter {

  Tracer tracer;
  TraceContext.Extractor<Map<String, String>> extractor;
  TraceContext.Injector<Map<String, String>> injector;
  volatile boolean isInit = false;

  /**
   * {@link ExtensionLoader} supplies the tracing implementation which must be named "tracing". For
   * example, if using the {@link SpringExtensionFactory}, only a bean named "tracing" will be
   * injected.
   */
  public void setTracing(Tracing tracing) {
    tracer = tracing.tracer();
    extractor = tracing.propagation().extractor(GETTER);
    injector = tracing.propagation().injector(SETTER);
    isInit = true;
  }

  @Override
  public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
    if (isInit == false) return invoker.invoke(invocation);

    RpcContext rpcContext = RpcContext.getContext();
    Kind kind = rpcContext.isProviderSide() ? Kind.SERVER : Kind.CLIENT;
    final Span span;
    if (kind.equals(Kind.CLIENT)) {
      span = tracer.nextSpan();
      injector.inject(span.context(), invocation.getAttachments());
    } else {
      TraceContextOrSamplingFlags extracted = extractor.extract(invocation.getAttachments());
      span = extracted.context() != null
        ? tracer.joinSpan(extracted.context())
        : tracer.nextSpan(extracted);
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
      if (future instanceof FutureAdapter) {
        deferFinish = true;
        ((FutureAdapter) future).getFuture().setCallback(new FinishSpanCallback(span));
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

  static final Propagation.Getter<Map<String, String>, String> GETTER =
    new Propagation.Getter<Map<String, String>, String>() {
      @Override
      public String get(Map<String, String> carrier, String key) {
        return carrier.get(key);
      }

      @Override
      public String toString() {
        return "Map::get";
      }
    };

  static final Propagation.Setter<Map<String, String>, String> SETTER =
    new Propagation.Setter<Map<String, String>, String>() {
      @Override
      public void put(Map<String, String> carrier, String key, String value) {
        carrier.put(key, value);
      }

      @Override
      public String toString() {
        return "Map::set";
      }
    };

  static final class FinishSpanCallback implements ResponseCallback {
    final Span span;

    FinishSpanCallback(Span span) {
      this.span = span;
    }

    @Override public void done(Object response) {
      span.finish();
    }

    @Override public void caught(Throwable exception) {
      onError(exception, span);
      span.finish();
    }
  }
}

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
import brave.SpanCustomizer;
import brave.Tag;
import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import brave.rpc.RpcClientHandler;
import brave.rpc.RpcResponse;
import brave.rpc.RpcResponseParser;
import brave.rpc.RpcServerHandler;
import brave.rpc.RpcTracing;
import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.extension.Activate;
import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.config.spring.extension.SpringExtensionFactory;
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

import static brave.internal.Throwables.propagateIfFatal;

@Activate(group = {Constants.PROVIDER, Constants.CONSUMER}, value = "tracing")
// http://dubbo.apache.org/en-us/docs/dev/impls/filter.html
// public constructor permitted to allow dubbo to instantiate this
public final class TracingFilter implements Filter {
  static final Tag<Throwable> DUBBO_ERROR_CODE = new Tag<Throwable>("dubbo.error_code") {
    @Override protected String parseValue(Throwable input, TraceContext context) {
      if (!(input instanceof RpcException)) return null;
      return String.valueOf(((RpcException) input).getCode());
    }
  };
  static final RpcResponseParser LEGACY_RESPONSE_PARSER = new RpcResponseParser() {
    @Override public void parse(RpcResponse response, TraceContext context, SpanCustomizer span) {
      DUBBO_ERROR_CODE.tag(response.error(), span);
    }
  };

  CurrentTraceContext currentTraceContext;
  RpcClientHandler clientHandler;
  RpcServerHandler serverHandler;
  volatile boolean isInit = false;

  /**
   * {@link ExtensionLoader} supplies the tracing implementation which must be named "tracing". For
   * example, if using the {@link SpringExtensionFactory}, only a bean named "tracing" will be
   * injected.
   *
   * @deprecated Since 5.12 only use {@link #setRpcTracing(RpcTracing)}
   */
  @Deprecated public void setTracing(Tracing tracing) {
    if (tracing == null) throw new NullPointerException("rpcTracing == null");
    setRpcTracing(RpcTracing.newBuilder(tracing)
        .clientResponseParser(LEGACY_RESPONSE_PARSER)
        .serverResponseParser(LEGACY_RESPONSE_PARSER)
        .build());
  }

  /**
   * {@link ExtensionLoader} supplies the tracing implementation which must be named "rpcTracing".
   * For example, if using the {@link SpringExtensionFactory}, only a bean named "rpcTracing" will
   * be injected.
   *
   * <h3>Custom parsing</h3>
   * Custom parsers, such as {@link RpcTracing#clientRequestParser()}, can use Dubbo-specific types
   * {@link DubboRequest} and {@link DubboResponse} to get access such as the Java invocation or
   * result.
   */
  public void setRpcTracing(RpcTracing rpcTracing) {
    if (rpcTracing == null) throw new NullPointerException("rpcTracing == null");
    // we don't guard on init because we intentionally want to overwrite any call to setTracing
    currentTraceContext = rpcTracing.tracing().currentTraceContext();
    clientHandler = RpcClientHandler.create(rpcTracing);
    serverHandler = RpcServerHandler.create(rpcTracing);
    isInit = true;
  }

  @Override public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
    if (!isInit) return invoker.invoke(invocation);
    TraceContext invocationContext = currentTraceContext.get();

    RpcContext rpcContext = RpcContext.getContext();
    Kind kind = rpcContext.isProviderSide() ? Kind.SERVER : Kind.CLIENT;
    Span span;
    DubboRequest request;
    if (kind.equals(Kind.CLIENT)) {
      // When A service invoke B service, then B service then invoke C service, the parentId of the
      // C service span is A when read from invocation.getAttachments(). This is because
      // AbstractInvoker adds attachments via RpcContext.getContext(), not the invocation.
      // See com.alibaba.dubbo.rpc.protocol.AbstractInvoker(line 138) from v2.6.7
      Map<String, String> attachments = RpcContext.getContext().getAttachments();
      DubboClientRequest clientRequest = new DubboClientRequest(invoker, invocation, attachments);
      request = clientRequest;
      span = clientHandler.handleSendWithParent(clientRequest, invocationContext);
    } else {
      DubboServerRequest serverRequest = new DubboServerRequest(invoker, invocation);
      request = serverRequest;
      span = serverHandler.handleReceive(serverRequest);
    }

    boolean isSynchronous = true;
    Scope scope = currentTraceContext.newScope(span.context());
    Result result = null;
    Throwable error = null;
    try {
      result = invoker.invoke(invocation);
      error = result.getException();
      Future<Object> future = rpcContext.getFuture(); // the case on async client invocation
      if (future != null) {
        if (!(future instanceof FutureAdapter)) {
          assert false : "we can't defer the span finish unless we can access the ResponseFuture";
          return result;
        }
        isSynchronous = false;
        ResponseFuture original = ((FutureAdapter<Object>) future).getFuture();
        // See instrumentation/RATIONALE.md for why the below response callbacks are invocation context
        TraceContext callbackContext = kind == Kind.CLIENT ? invocationContext : span.context();
        ResponseFuture wrapped =
            new FinishSpanResponseFuture(original, this, request, result, span, callbackContext);
        RpcContext.getContext().setFuture(new FutureAdapter<>(wrapped));
      }
      return result;
    } catch (Throwable e) {
      propagateIfFatal(e);
      error = e;
      throw e;
    } finally {
      if (isSynchronous) FinishSpan.finish(this, request, result, error, span);
      scope.close();
    }
  }
}

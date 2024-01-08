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
import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import com.alibaba.dubbo.remoting.RemotingException;
import com.alibaba.dubbo.remoting.exchange.ResponseCallback;
import com.alibaba.dubbo.remoting.exchange.ResponseFuture;
import com.alibaba.dubbo.rpc.Result;

/** Ensures any callbacks finish the span. */
final class FinishSpanResponseFuture implements ResponseFuture {
  final ResponseFuture delegate;
  final FinishSpan finishSpan;
  final CurrentTraceContext currentTraceContext;
  final @Nullable TraceContext callbackContext;

  FinishSpanResponseFuture(
    ResponseFuture delegate, TracingFilter filter, DubboRequest request, Result result, Span span,
    @Nullable TraceContext callbackContext) {
    this.delegate = delegate;
    this.finishSpan = FinishSpan.create(filter, request, result, span);
    this.currentTraceContext = filter.currentTraceContext;
    this.callbackContext = callbackContext;
    // Ensures even if no callback added later, for example when a consumer, we finish the span
    setCallback(null);
  }

  @Override public Object get() throws RemotingException {
    return delegate.get();
  }

  @Override public Object get(int timeoutInMillis) throws RemotingException {
    return delegate.get(timeoutInMillis);
  }

  @Override public void setCallback(ResponseCallback callback) {
    delegate.setCallback(
      TracingResponseCallback.create(callback, finishSpan, currentTraceContext, callbackContext)
    );
  }

  @Override public boolean isDone() {
    return delegate.isDone();
  }
}

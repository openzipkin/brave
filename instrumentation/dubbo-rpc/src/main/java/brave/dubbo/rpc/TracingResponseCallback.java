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

import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import com.alibaba.dubbo.remoting.exchange.ResponseCallback;

/**
 * Ensures deferred async calls complete a span upon success or failure callback.
 *
 * <p>This was originally a copy of {@code brave.kafka.clients.TracingCallback}.
 */
class TracingResponseCallback implements ResponseCallback {
  static ResponseCallback create(
    @Nullable ResponseCallback delegate, FinishSpan finishSpan,
    CurrentTraceContext currentTraceContext, @Nullable TraceContext context) {
    if (delegate == null) return new TracingResponseCallback(finishSpan);
    return new DelegateAndFinishSpan(finishSpan, delegate, currentTraceContext, context);
  }

  final FinishSpan finishSpan;

  TracingResponseCallback(FinishSpan finishSpan) {
    this.finishSpan = finishSpan;
  }

  @Override public void done(Object response) {
    finishSpan.accept(response, null);
  }

  @Override public void caught(Throwable exception) {
    finishSpan.accept(null, exception);
  }

  static final class DelegateAndFinishSpan extends TracingResponseCallback {
    final ResponseCallback delegate;
    final CurrentTraceContext current;
    @Nullable final TraceContext context;

    DelegateAndFinishSpan(FinishSpan finishSpan, ResponseCallback delegate,
      CurrentTraceContext currentTraceContext, @Nullable TraceContext context) {
      super(finishSpan);
      this.delegate = delegate;
      this.current = currentTraceContext;
      this.context = context;
    }

    @Override public void done(Object response) {
      try (Scope ws = current.maybeScope(context)) {
        delegate.done(response);
      } finally {
        super.done(response);
      }
    }

    @Override public void caught(Throwable exception) {
      try (Scope ws = current.maybeScope(context)) {
        delegate.caught(exception);
      } finally {
        super.caught(exception);
      }
    }
  }
}

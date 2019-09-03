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
import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import com.alibaba.dubbo.remoting.exchange.ResponseCallback;

import static brave.dubbo.rpc.TracingFilter.onError;

/**
 * Ensures deferred async calls complete a span upon success or failure callback.
 *
 * <p>This is an exact design copy of {@code brave.kafka.clients.TracingCallback}.
 */
final class TracingResponseCallback {
  static ResponseCallback create(@Nullable ResponseCallback delegate, Span span,
    CurrentTraceContext current) {
    if (delegate == null) return new FinishSpan(span);
    return new DelegateAndFinishSpan(delegate, span, current);
  }

  static class FinishSpan implements ResponseCallback {
    final Span span;

    FinishSpan(Span span) {
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

  static final class DelegateAndFinishSpan extends FinishSpan {
    final ResponseCallback delegate;
    final CurrentTraceContext current;

    DelegateAndFinishSpan(ResponseCallback delegate, Span span, CurrentTraceContext current) {
      super(span);
      this.delegate = delegate;
      this.current = current;
    }

    @Override public void done(Object response) {
      try (CurrentTraceContext.Scope ws = current.maybeScope(span.context())) {
        delegate.done(response);
      } finally {
        super.done(response);
      }
    }

    @Override public void caught(Throwable exception) {
      try (CurrentTraceContext.Scope ws = current.maybeScope(span.context())) {
        delegate.caught(exception);
      } finally {
        super.caught(exception);
      }
    }
  }
}

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
package brave.jms;

import brave.Span;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import javax.jms.CompletionListener;
import javax.jms.Message;

/**
 * Decorates, then finishes a producer span. Allows tracing to record the duration between batching
 * for send and actual send.
 */
@JMS2_0 final class TracingCompletionListener implements CompletionListener {
  static CompletionListener create(CompletionListener delegate, Span span,
    CurrentTraceContext current) {
    if (span.isNoop()) return delegate; // save allocation overhead
    return new TracingCompletionListener(delegate, span, current);
  }

  final Span span;
  final CompletionListener delegate;
  final CurrentTraceContext current;

  TracingCompletionListener(CompletionListener delegate, Span span, CurrentTraceContext current) {
    this.span = span;
    this.delegate = delegate;
    this.current = current;
  }

  @Override public void onCompletion(Message message) {
    try (Scope ws = current.maybeScope(span.context())) {
      delegate.onCompletion(message);
    } finally {
      span.finish();
    }
  }

  @Override public void onException(Message message, Exception exception) {
    try {
      delegate.onException(message, exception);
      span.error(exception);
    } finally {
      span.finish();
    }
  }
}

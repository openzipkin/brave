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
package brave.kafka.clients;

import brave.Span;
import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.RecordMetadata;

/**
 * Decorates, then finishes a producer span. Allows tracing to record the duration between batching
 * for send and actual send.
 */
final class TracingCallback {
  static Callback create(@Nullable Callback delegate, Span span, CurrentTraceContext current) {
    if (span.isNoop()) return delegate; // save allocation overhead
    if (delegate == null) return new FinishSpan(span);
    return new DelegateAndFinishSpan(delegate, span, current);
  }

  static class FinishSpan implements Callback {
    final Span span;

    FinishSpan(Span span) {
      this.span = span;
    }

    @Override public void onCompletion(RecordMetadata metadata, @Nullable Exception exception) {
      if (exception != null) span.error(exception);
      span.finish();
    }
  }

  static final class DelegateAndFinishSpan extends FinishSpan {
    final Callback delegate;
    final CurrentTraceContext current;

    DelegateAndFinishSpan(Callback delegate, Span span, CurrentTraceContext current) {
      super(span);
      this.delegate = delegate;
      this.current = current;
    }

    @Override public void onCompletion(RecordMetadata metadata, @Nullable Exception exception) {
      try (Scope ws = current.maybeScope(span.context())) {
        delegate.onCompletion(metadata, exception);
      } finally {
        super.onCompletion(metadata, exception);
      }
    }
  }
}

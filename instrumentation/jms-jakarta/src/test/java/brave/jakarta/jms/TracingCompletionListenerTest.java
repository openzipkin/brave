/*
 * Copyright 2013-2024 The OpenZipkin Authors
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
package brave.jakarta.jms;

import brave.Span;
import brave.propagation.TraceContextOrSamplingFlags;
import jakarta.jms.CompletionListener;
import jakarta.jms.Message;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class TracingCompletionListenerTest extends ITJms {
  Message message = mock(Message.class);

  @Test void onCompletion_shouldKeepContext_whenNotSampled() {
    Span span = tracing.tracer().nextSpan(TraceContextOrSamplingFlags.NOT_SAMPLED);

    CompletionListener delegate = new CompletionListener() {
      @Override public void onCompletion(Message message) {
        assertThat(currentTraceContext.get()).isEqualTo(span.context());
      }

      @Override public void onException(Message message, Exception exception) {
      }
    };
    CompletionListener tracingCompletionListener =
      TracingCompletionListener.create(delegate, span, currentTraceContext);

    tracingCompletionListener.onCompletion(null);

    // post-conditions validate no span was reported
  }

  @Test void on_completion_should_finish_span() {
    Span span = tracing.tracer().nextSpan().start();

    CompletionListener tracingCompletionListener =
      TracingCompletionListener.create(mock(CompletionListener.class), span,
        currentTraceContext);
    tracingCompletionListener.onCompletion(message);

    testSpanHandler.takeLocalSpan();
  }

  @Test void on_exception_should_set_error_if_exception() {
    Message message = mock(Message.class);
    Span span = tracing.tracer().nextSpan().start();

    RuntimeException error = new RuntimeException("Test exception");
    CompletionListener tracingCompletionListener =
      TracingCompletionListener.create(mock(CompletionListener.class), span,
        currentTraceContext);
    tracingCompletionListener.onException(message, error);

    assertThat(testSpanHandler.takeLocalSpan().error()).isEqualTo(error);
  }

  @Test void on_completion_should_forward_then_finish_span() {
    Span span = tracing.tracer().nextSpan().start();

    CompletionListener delegate = mock(CompletionListener.class);
    CompletionListener tracingCompletionListener =
      TracingCompletionListener.create(delegate, span, currentTraceContext);
    tracingCompletionListener.onCompletion(message);

    verify(delegate).onCompletion(message);

    testSpanHandler.takeLocalSpan();
  }

  @Test void on_completion_should_have_span_in_scope() {
    Span span = tracing.tracer().nextSpan().start();

    CompletionListener delegate = new CompletionListener() {
      @Override public void onCompletion(Message message) {
        assertThat(currentTraceContext.get()).isSameAs(span.context());
      }

      @Override public void onException(Message message, Exception exception) {
        throw new AssertionError();
      }
    };

    TracingCompletionListener.create(delegate, span, currentTraceContext)
      .onCompletion(message);

    testSpanHandler.takeLocalSpan();
  }

  @Test void on_exception_should_forward_then_set_error() {
    Span span = tracing.tracer().nextSpan().start();

    CompletionListener delegate = mock(CompletionListener.class);
    CompletionListener tracingCompletionListener =
      TracingCompletionListener.create(delegate, span, currentTraceContext);
    RuntimeException error = new RuntimeException("Test exception");
    tracingCompletionListener.onException(message, error);

    verify(delegate).onException(message, error);

    assertThat(testSpanHandler.takeLocalSpan().error()).isEqualTo(error);
  }
}

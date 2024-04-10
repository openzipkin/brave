/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jms;

import brave.Span;
import brave.propagation.TraceContextOrSamplingFlags;
import javax.jms.CompletionListener;
import javax.jms.Message;
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
      TracingCompletionListener.create(mock(CompletionListener.class), span, currentTraceContext);
    tracingCompletionListener.onCompletion(message);

    testSpanHandler.takeLocalSpan();
  }

  @Test void on_exception_should_set_error_if_exception() {
    Message message = mock(Message.class);
    Span span = tracing.tracer().nextSpan().start();

    RuntimeException error = new RuntimeException("Test exception");
    CompletionListener tracingCompletionListener =
      TracingCompletionListener.create(mock(CompletionListener.class), span, currentTraceContext);
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

    TracingCompletionListener.create(delegate, span, currentTraceContext).onCompletion(message);

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

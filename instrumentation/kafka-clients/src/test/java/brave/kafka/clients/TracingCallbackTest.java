/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.kafka.clients;

import brave.Span;
import brave.propagation.TraceContextOrSamplingFlags;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class TracingCallbackTest extends KafkaTest {
  @Test void onCompletion_shouldKeepContext_whenNotSampled() {
    Span span = tracing.tracer().nextSpan(TraceContextOrSamplingFlags.NOT_SAMPLED);

    Callback delegate =
      (metadata, exception) -> assertThat(tracing.tracer().currentSpan()).isEqualTo(span);
    Callback tracingCallback = TracingCallback.create(delegate, span, tracing.currentTraceContext());

    tracingCallback.onCompletion(null, null);
  }

  @Test void on_completion_should_finish_span() {
    Span span = tracing.tracer().nextSpan().start();

    Callback tracingCallback = TracingCallback.create(null, span, currentTraceContext);
    tracingCallback.onCompletion(createRecordMetadata(), null);

    assertThat(spans.get(0).finishTimestamp()).isNotZero();
  }

  @Test void on_completion_should_tag_if_exception() {
    Span span = tracing.tracer().nextSpan().start();

    Callback tracingCallback = TracingCallback.create(null, span, currentTraceContext);
    tracingCallback.onCompletion(null, error);

    assertThat(spans.get(0).finishTimestamp()).isNotZero();
    assertThat(spans.get(0).error()).isEqualTo(error);
  }

  @Test void on_completion_should_forward_then_finish_span() {
    Span span = tracing.tracer().nextSpan().start();

    Callback delegate = mock(Callback.class);
    Callback tracingCallback = TracingCallback.create(delegate, span, currentTraceContext);
    RecordMetadata md = createRecordMetadata();
    tracingCallback.onCompletion(md, null);

    verify(delegate).onCompletion(md, null);

    assertThat(spans.get(0).finishTimestamp()).isNotZero();
  }

  @Test void on_completion_should_have_span_in_scope() {
    Span span = tracing.tracer().nextSpan().start();

    Callback delegate =
      (metadata, exception) -> assertThat(currentTraceContext.get()).isSameAs(span.context());

    TracingCallback.create(delegate, span, currentTraceContext)
      .onCompletion(createRecordMetadata(), null);

    assertThat(spans.get(0).finishTimestamp()).isNotZero();
  }

  @Test void on_completion_should_forward_then_tag_if_exception() {
    Span span = tracing.tracer().nextSpan().start();

    Callback delegate = mock(Callback.class);
    Callback tracingCallback = TracingCallback.create(delegate, span, currentTraceContext);
    RecordMetadata md = createRecordMetadata();
    tracingCallback.onCompletion(md, error);

    verify(delegate).onCompletion(md, error);

    assertThat(spans.get(0).finishTimestamp()).isNotZero();
    assertThat(spans.get(0).error()).isEqualTo(error);
  }
}

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
package brave.kafka.clients;

import brave.Span;
import brave.propagation.TraceContextOrSamplingFlags;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class TracingCallbackTest extends ITKafka {
  @Test public void onCompletion_shouldKeepContext_whenNotSampled() {
    Span span = tracing.tracer().nextSpan(TraceContextOrSamplingFlags.NOT_SAMPLED);

    Callback delegate =
      (metadata, exception) -> assertThat(tracing.tracer().currentSpan()).isEqualTo(span);
    Callback tracingCallback = TracingCallback.create(delegate, span, currentTraceContext);

    tracingCallback.onCompletion(null, null);
  }

  @Test public void on_completion_should_finish_span() {
    Span span = tracing.tracer().nextSpan().start();

    Callback tracingCallback = TracingCallback.create(null, span, currentTraceContext);
    tracingCallback.onCompletion(createRecordMetadata(), null);

    reporter.takeLocalSpan();
  }

  @Test public void on_completion_should_tag_if_exception() {
    Span span = tracing.tracer().nextSpan().start();

    Callback tracingCallback = TracingCallback.create(null, span, currentTraceContext);
    tracingCallback.onCompletion(null, new Exception("Test exception"));

    reporter.takeLocalSpanWithError("Test exception");
  }

  @Test public void on_completion_should_forward_then_finish_span() {
    Span span = tracing.tracer().nextSpan().start();

    Callback delegate = mock(Callback.class);
    Callback tracingCallback = TracingCallback.create(delegate, span, currentTraceContext);
    RecordMetadata md = createRecordMetadata();
    tracingCallback.onCompletion(md, null);

    verify(delegate).onCompletion(md, null);

    reporter.takeLocalSpan();
  }

  @Test public void on_completion_should_have_span_in_scope() {
    Span span = tracing.tracer().nextSpan().start();

    Callback delegate =
      (metadata, exception) -> assertThat(currentTraceContext.get()).isSameAs(span.context());

    TracingCallback.create(delegate, span, currentTraceContext)
      .onCompletion(createRecordMetadata(), null);

    reporter.takeLocalSpan();
  }

  @Test public void on_completion_should_forward_then_tag_if_exception() {
    Span span = tracing.tracer().nextSpan().start();

    Callback delegate = mock(Callback.class);
    Callback tracingCallback = TracingCallback.create(delegate, span, currentTraceContext);
    RecordMetadata md = createRecordMetadata();
    Exception e = new Exception("Test exception");
    tracingCallback.onCompletion(md, e);

    verify(delegate).onCompletion(md, e);

    reporter.takeLocalSpanWithError("Test exception");
  }

  RecordMetadata createRecordMetadata() {
    TopicPartition tp = new TopicPartition("foo", 0);
    long timestamp = 2340234L;
    int keySize = 3;
    int valueSize = 5;
    Long checksum = 908923L;
    return new RecordMetadata(tp, -1L, -1L, timestamp, checksum, keySize, valueSize);
  }
}

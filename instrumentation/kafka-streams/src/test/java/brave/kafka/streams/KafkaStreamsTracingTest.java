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
package brave.kafka.streams;

import brave.Span;
import brave.propagation.CurrentTraceContext.Scope;
import java.util.Date;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.junit.jupiter.api.Test;

import static brave.test.ITRemote.BAGGAGE_FIELD;
import static brave.test.ITRemote.BAGGAGE_FIELD_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class KafkaStreamsTracingTest extends KafkaStreamsTest {
  @Test void nextSpan_uses_current_context() {
    ProcessorContext fakeProcessorContext = processorContextSupplier.apply(new RecordHeaders());
    Span child;
    try (Scope scope = tracing.currentTraceContext().newScope(parent)) {
      child = kafkaStreamsTracing.nextSpan(fakeProcessorContext);
    }
    child.finish();

    assertThat(child.context().parentIdString())
        .isEqualTo(parent.spanIdString());
  }

  @Test void nextSpanWithHeaders_uses_current_context() {
    org.apache.kafka.streams.processor.api.ProcessorContext<String, String> fakeProcessorContext = processorV2ContextSupplier.get();
    Span child;
    try (Scope scope = tracing.currentTraceContext().newScope(parent)) {
      child = kafkaStreamsTracing.nextSpan(fakeProcessorContext, new RecordHeaders());
    }
    child.finish();

    assertThat(child.context().parentIdString())
        .isEqualTo(parent.spanIdString());
  }

  @Test void nextSpan_should_create_span_if_no_headers() {
    ProcessorContext fakeProcessorContext = processorContextSupplier.apply(new RecordHeaders());
    assertThat(kafkaStreamsTracing.nextSpan(fakeProcessorContext)).isNotNull();
  }

  @Test void nextSpanWithHeaders_should_create_span_if_no_headers() {
    org.apache.kafka.streams.processor.api.ProcessorContext<String, String> fakeProcessorContext = processorV2ContextSupplier.get();
    assertThat(kafkaStreamsTracing.nextSpan(fakeProcessorContext, new RecordHeaders())).isNotNull();
  }

  @Test void nextSpan_should_tag_app_id_and_task_id() {
    ProcessorContext fakeProcessorContext = processorContextSupplier.apply(new RecordHeaders());
    kafkaStreamsTracing.nextSpan(fakeProcessorContext).start().finish();

    assertThat(spans.get(0).tags())
      .containsOnly(
        entry("kafka.streams.application.id", TEST_APPLICATION_ID),
        entry("kafka.streams.task.id", TEST_TASK_ID));
  }

  @Test void nextSpanWithHeaders_should_tag_app_id_and_task_id() {
    org.apache.kafka.streams.processor.api.ProcessorContext<String, String> fakeProcessorContext = processorV2ContextSupplier.get();
    kafkaStreamsTracing.nextSpan(fakeProcessorContext, new RecordHeaders()).start().finish();

    assertThat(spans.get(0).tags())
      .containsOnly(
        entry("kafka.streams.application.id", TEST_APPLICATION_ID),
        entry("kafka.streams.task.id", TEST_TASK_ID));
  }

  @Test void newProcessorSupplier_should_tag_app_id_and_task_id() {
    org.apache.kafka.streams.processor.api.Processor<String, String, String, String> processor = fakeV2ProcessorSupplier.get();
    processor.init(processorV2ContextSupplier.get());
    processor.process(new Record<>(TEST_KEY, TEST_VALUE, new Date().getTime()));

    assertThat(spans.get(0).tags())
      .containsOnly(
        entry("kafka.streams.application.id", TEST_APPLICATION_ID),
        entry("kafka.streams.task.id", TEST_TASK_ID));
  }

  @Test void newProcessorSupplier_should_add_baggage_field() {
    org.apache.kafka.streams.processor.api.ProcessorSupplier<String, String, String, String> processorSupplier =
      kafkaStreamsTracing.process(
        "forward-1", () ->
          (org.apache.kafka.streams.processor.api.Processor<String, String, String, String>) record ->
            assertThat(BAGGAGE_FIELD.getValue(currentTraceContext.get())).isEqualTo("user1"));
    Headers headers = new RecordHeaders().add(BAGGAGE_FIELD_KEY, "user1".getBytes());
    org.apache.kafka.streams.processor.api.Processor<String, String, String, String> processor = processorSupplier.get();
    processor.init(processorV2ContextSupplier.get());
    processor.process(new Record<>(TEST_KEY, TEST_VALUE, new Date().getTime(), headers));
  }
}

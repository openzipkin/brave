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

import brave.Tracing;
import brave.baggage.BaggagePropagation;
import brave.baggage.BaggagePropagationConfig;
import brave.propagation.B3Propagation;
import brave.propagation.StrictCurrentTraceContext;
import brave.propagation.TraceContext;
import brave.test.TestSpanHandler;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.TaskId;
import org.apache.kafka.streams.processor.api.Record;

import static brave.test.ITRemote.BAGGAGE_FIELD;
import static brave.test.ITRemote.BAGGAGE_FIELD_KEY;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KafkaStreamsTest {
  static final String TEST_APPLICATION_ID = "myAppId";
  static final String TEST_TASK_ID = "0_0";
  static final String TEST_TOPIC = "myTopic";
  static final String TEST_KEY = "foo";
  static final String TEST_VALUE = "bar";

  StrictCurrentTraceContext currentTraceContext = StrictCurrentTraceContext.create();
  TestSpanHandler spans = new TestSpanHandler();
  Tracing tracing = Tracing.newBuilder()
      .currentTraceContext(currentTraceContext)
      .addSpanHandler(spans)
      .propagationFactory(BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY)
          .add(BaggagePropagationConfig.SingleBaggageField.newBuilder(BAGGAGE_FIELD)
              .addKeyName(BAGGAGE_FIELD_KEY)
              .build()).build())
      .build();

  KafkaStreamsTracing kafkaStreamsTracing = KafkaStreamsTracing.create(tracing);
  TraceContext parent = tracing.tracer().newTrace().context();

  Function<Headers, ProcessorContext> processorContextSupplier =
      (Headers headers) ->
      {
        ProcessorContext processorContext = mock(ProcessorContext.class);
        when(processorContext.applicationId()).thenReturn(TEST_APPLICATION_ID);
        when(processorContext.topic()).thenReturn(TEST_TOPIC);
        when(processorContext.taskId()).thenReturn(new TaskId(0, 0));
        when(processorContext.headers()).thenReturn(headers);
        return processorContext;
      };

  Supplier<org.apache.kafka.streams.processor.api.ProcessorContext<String, String>> processorV2ContextSupplier =
    () ->
    {
      org.apache.kafka.streams.processor.api.ProcessorContext processorContext = mock(org.apache.kafka.streams.processor.api.ProcessorContext.class);
      when(processorContext.applicationId()).thenReturn(TEST_APPLICATION_ID);
      when(processorContext.taskId()).thenReturn(new TaskId(0, 0));
      return processorContext;
    };

  org.apache.kafka.streams.processor.api.ProcessorSupplier<String, String, String, String> fakeV2ProcessorSupplier =
    kafkaStreamsTracing.process(
      "forward-1", () ->
        new org.apache.kafka.streams.processor.api.Processor<String, String, String, String>() {
          org.apache.kafka.streams.processor.api.ProcessorContext context;
          @Override
          public void init(org.apache.kafka.streams.processor.api.ProcessorContext<String, String> context) {
            this.context = context;
          }
          @Override
          public void process(Record record) {
            context.forward(record);
          }
        });
}

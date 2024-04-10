/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.kafka.streams;

import brave.Tracing;
import brave.baggage.BaggagePropagation;
import brave.baggage.BaggagePropagationConfig;
import brave.propagation.B3Propagation;
import brave.propagation.StrictCurrentTraceContext;
import brave.propagation.TraceContext;
import brave.test.TestSpanHandler;
import java.util.function.Supplier;
import org.apache.kafka.streams.processor.TaskId;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.ProcessorSupplier;
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

  Supplier<ProcessorContext<String, String>> processorV2ContextSupplier = () -> {
    ProcessorContext<String, String> processorContext =
      mock(ProcessorContext.class);
    when(processorContext.applicationId()).thenReturn(TEST_APPLICATION_ID);
    when(processorContext.taskId()).thenReturn(new TaskId(0, 0));
    return processorContext;
  };

  ProcessorSupplier<String, String, String, String> fakeV2ProcessorSupplier =
    kafkaStreamsTracing.process("forward-1", () -> new Processor<>() {
      ProcessorContext<String, String> context;

      @Override public void init(ProcessorContext<String, String> context) {
        this.context = context;
      }

      @Override public void process(Record<String, String> record) {
        context.forward(record);
      }
    });
}

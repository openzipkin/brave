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
package brave.kafka.streams;

import brave.messaging.MessagingTracing;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import brave.test.ITRemote;
import brave.test.util.AssertableCallback;
import java.util.function.Function;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.kstream.TransformerSupplier;
import org.apache.kafka.streams.kstream.ValueTransformer;
import org.apache.kafka.streams.kstream.ValueTransformerSupplier;
import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.kstream.ValueTransformerWithKeySupplier;
import org.apache.kafka.streams.processor.AbstractProcessor;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.processor.ProcessorSupplier;
import org.apache.kafka.streams.processor.TaskId;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

abstract class ITKafkaStreams extends ITRemote {
  String TEST_APPLICATION_ID = "myAppId";
  String TEST_TASK_ID = "0_0";
  String TEST_TOPIC = "myTopic";
  String TEST_KEY = "foo";
  String TEST_VALUE = "bar";

  MessagingTracing messagingTracing = MessagingTracing.create(tracing);
  KafkaStreamsTracing kafkaStreamsTracing = KafkaStreamsTracing.create(messagingTracing);
  TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);

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

  ProcessorSupplier<String, String> fakeProcessorSupplier =
    kafkaStreamsTracing.processor(
      "forward-1", () ->
        new AbstractProcessor<String, String>() {
          @Override
          public void process(String key, String value) {
            context().forward(key, value);
          }
        });

  TransformerSupplier<String, String, KeyValue<String, String>> fakeTransformerSupplier =
    kafkaStreamsTracing.transformer(
      "transformer-1", () ->
        new Transformer<String, String, KeyValue<String, String>>() {
          ProcessorContext context;

          @Override
          public void init(ProcessorContext context) {
            this.context = context;
          }

          @Override
          public KeyValue<String, String> transform(String key, String value) {
            return KeyValue.pair(key, value);
          }

          @Override
          public void close() {
          }
        });

  ValueTransformerSupplier<String, String> fakeValueTransformerSupplier =
    kafkaStreamsTracing.valueTransformer(
      "value-transformer-1", () ->
        new ValueTransformer<String, String>() {
          ProcessorContext context;

          @Override
          public void init(ProcessorContext context) {
            this.context = context;
          }

          @Override
          public String transform(String value) {
            return value;
          }

          @Override
          public void close() {
          }
        });

  ValueTransformerWithKeySupplier<String, String, String> fakeValueTransformerWithKeySupplier =
    kafkaStreamsTracing.valueTransformerWithKey(
      "value-transformer-1", () ->
        new ValueTransformerWithKey<String, String, String>() {
          ProcessorContext context;

          @Override
          public void init(ProcessorContext context) {
            this.context = context;
          }

          @Override
          public String transform(String key, String value) {
            return value;
          }

          @Override
          public void close() {
          }
        });

  /** {@link #join()} waits for the callback to complete without any errors */
  static final class BlockingCallback implements Callback {
    final AssertableCallback<RecordMetadata> delegate = new AssertableCallback<>();

    void join() {
      delegate.join();
    }

    @Override public void onCompletion(RecordMetadata metadata, Exception exception) {
      if (exception != null) {
        delegate.onError(exception);
      } else {
        delegate.onSuccess(metadata);
      }
    }
  }
}

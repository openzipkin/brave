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

import org.apache.kafka.common.header.Headers;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;

final class TracingProcessor<KIn, VIn, KOut, VOut> extends
  BaseTracingProcessor<ProcessorContext<KOut, VOut>, Record<KIn, VIn>, Processor<KIn, VIn, KOut, VOut>>
  implements Processor<KIn, VIn, KOut, VOut> {

  TracingProcessor(KafkaStreamsTracing kafkaStreamsTracing, String spanName,
    Processor<KIn, VIn, KOut, VOut> delegate) {
    super(kafkaStreamsTracing, spanName, delegate);
  }

  @Override Headers headers(Record<KIn, VIn> record) {
    return record.headers();
  }

  @Override void process(Processor<KIn, VIn, KOut, VOut> delegate, Record<KIn, VIn> record) {
    delegate.process(record);
  }

  @Override public void init(ProcessorContext<KOut, VOut> context) {
    this.context = context;
    delegate.init(context);
  }

  @Override public void close() {
    delegate.close();
  }
}

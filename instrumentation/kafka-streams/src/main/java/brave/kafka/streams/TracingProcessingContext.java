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

import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Injector;
import java.io.File;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.streams.StreamsMetrics;
import org.apache.kafka.streams.processor.Cancellable;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.Punctuator;
import org.apache.kafka.streams.processor.StateStore;
import org.apache.kafka.streams.processor.TaskId;
import org.apache.kafka.streams.processor.api.ProcessingContext;
import org.apache.kafka.streams.processor.api.RecordMetadata;

abstract class TracingProcessingContext<C extends ProcessingContext> implements ProcessingContext {
  final C delegate;
  final Injector<Headers> injector;
  final TraceContext context;

  TracingProcessingContext(C delegate, Injector<Headers> injector,
    TraceContext context) {
    this.delegate = delegate;
    this.injector = injector;
    this.context = context;
  }

  @Override public String applicationId() {
    return delegate.applicationId();
  }

  @Override public TaskId taskId() {
    return delegate.taskId();
  }

  @Override public Optional<RecordMetadata> recordMetadata() {
    return delegate.recordMetadata();
  }

  @Override public Serde<?> keySerde() {
    return delegate.keySerde();
  }

  @Override public Serde<?> valueSerde() {
    return delegate.valueSerde();
  }

  @Override public File stateDir() {
    return delegate.stateDir();
  }

  @Override public StreamsMetrics metrics() {
    return delegate.metrics();
  }

  @Override public <S extends StateStore> S getStateStore(String s) {
    return delegate.getStateStore(s);
  }

  @Override public Cancellable schedule(Duration duration, PunctuationType punctuationType,
    Punctuator punctuator) {
    return delegate.schedule(duration, punctuationType, punctuator);
  }

  @Override public void commit() {
    delegate.commit();
  }

  @Override public Map<String, Object> appConfigs() {
    return delegate.appConfigs();
  }

  @Override public Map<String, Object> appConfigsWithPrefix(String s) {
    return delegate.appConfigsWithPrefix(s);
  }

  @Override public long currentSystemTimeMs() {
    return delegate.currentSystemTimeMs();
  }

  @Override public long currentStreamTimeMs() {
    return delegate.currentStreamTimeMs();
  }
}

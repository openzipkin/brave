/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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

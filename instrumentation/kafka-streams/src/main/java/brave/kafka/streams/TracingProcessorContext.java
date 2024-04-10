/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.kafka.streams;

import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Injector;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;

/** Injects the initialization tracing context to record headers on forward */
final class TracingProcessorContext<KForward, VForward>
  extends TracingProcessingContext<ProcessorContext<KForward, VForward>>
  implements ProcessorContext<KForward, VForward> {

  TracingProcessorContext(ProcessorContext<KForward, VForward> delegate,
    Injector<Headers> injector, TraceContext context) {
    super(delegate, injector, context);
  }

  @Override public <K extends KForward, V extends VForward> void forward(Record<K, V> r) {
    injector.inject(context, r.headers());
    delegate.forward(r);
  }

  @Override
  public <K extends KForward, V extends VForward> void forward(Record<K, V> r, String s) {
    injector.inject(context, r.headers());
    delegate.forward(r, s);
  }
}

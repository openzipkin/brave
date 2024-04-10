/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.kafka.streams;

import brave.propagation.TraceContext;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.streams.processor.api.FixedKeyProcessorContext;
import org.apache.kafka.streams.processor.api.FixedKeyRecord;

/** Injects the initialization tracing context to record headers on forward */
final class TracingFixedKeyProcessorContext<KForward, VForward>
  extends TracingProcessingContext<FixedKeyProcessorContext<KForward, VForward>>
  implements FixedKeyProcessorContext<KForward, VForward> {

  TracingFixedKeyProcessorContext(FixedKeyProcessorContext<KForward, VForward> delegate,
    TraceContext.Injector<Headers> injector, TraceContext context) {
    super(delegate, injector, context);
  }

  @Override public <K extends KForward, V extends VForward> void forward(FixedKeyRecord<K, V> r) {
    injector.inject(context, r.headers());
    delegate.forward(r);
  }

  @Override
  public <K extends KForward, V extends VForward> void forward(FixedKeyRecord<K, V> r, String s) {
    injector.inject(context, r.headers());
    delegate.forward(r, s);
  }
}

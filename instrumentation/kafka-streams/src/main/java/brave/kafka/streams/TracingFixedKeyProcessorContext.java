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

/*
 * Copyright 2013-2019 The OpenZipkin Authors
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
package brave.http.features.secondary_sampling;

import brave.http.features.secondary_sampling.SecondarySampling.Extra;
import brave.propagation.Propagation.Setter;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Injector;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * This writes the {@link SecondarySampling#FIELD_NAME sampling header}, with an updated {@code
 * spanId} parameters for each sampled key. The {@link TraceForwarder} can use that span ID to correct
 * the parent hierarchy.
 */
final class SecondarySamplingInjector<C, K> implements Injector<C> {
  final Injector<C> delegate;
  final Setter<C, K> setter;
  final K samplingKey;

  SecondarySamplingInjector(SecondarySampling.Propagation<K> propagation, Setter<C, K> setter) {
    this.delegate = propagation.delegate.injector(setter);
    this.setter = setter;
    this.samplingKey = propagation.samplingKey;
  }

  @Override public void inject(TraceContext traceContext, C carrier) {
    delegate.inject(traceContext, carrier);
    Extra extra = traceContext.findExtra(Extra.class);
    if (extra == null || extra.samplingKeyToParameters.isEmpty()) return;
    setter.put(carrier, samplingKey, serializeWithSpanId(extra, traceContext.spanIdString()));
  }

  static String serializeWithSpanId(Extra extra, String spanId) {
    return extra.samplingKeyToParameters.entrySet()
      .stream()
      .map(e -> serializeWithSpanId(extra, e.getKey(), e.getValue(), spanId))
      .collect(Collectors.joining(","));
  }

  static String serializeWithSpanId(Extra extra, String samplingKey, Map<String, String> parameters,
    String spanId) {
    StringJoiner joiner = new StringJoiner(";");
    joiner.add(samplingKey);
    String lastSpanId = null;
    for (Map.Entry<String, String> entry : parameters.entrySet()) {
      if ("spanId".equals(entry.getKey())) {
        lastSpanId = entry.getValue();
        continue;
      }
      joiner.add(entry.getKey() + "=" + entry.getValue());
    }

    if (extra.sampledKeys.contains(samplingKey)) {
      joiner.add("spanId=" + spanId);
    } else if (lastSpanId != null) { // pass through the last span ID
      joiner.add("spanId=" + lastSpanId);
    }

    return joiner.toString();
  }
}

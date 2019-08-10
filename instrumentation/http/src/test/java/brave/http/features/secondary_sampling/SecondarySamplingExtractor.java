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

import brave.http.features.secondary_sampling.SecondarySamplingPolicy.Trigger;
import brave.propagation.Propagation.Getter;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.LinkedHashMap;
import java.util.Map;

import static brave.http.features.secondary_sampling.SecondarySampling.parseParameters;

/**
 * This extracts the {@link SecondarySampling#FIELD_NAME sampling header}, and performs any TTL or
 * triggering logic based on the policy configured for this service.
 */
final class SecondarySamplingExtractor<C, K> implements Extractor<C> {
  final Extractor<C> delegate;
  final Getter<C, K> getter;
  final SecondarySamplingPolicy policy;
  final String localServiceName;
  final K samplingKey;

  SecondarySamplingExtractor(SecondarySampling.Propagation<K> propagation, Getter<C, K> getter) {
    this.delegate = propagation.delegate.extractor(getter);
    this.getter = getter;
    this.policy = propagation.secondarySampling.policy;
    this.localServiceName = propagation.secondarySampling.localServiceName;
    this.samplingKey = propagation.samplingKey;
  }

  @Override public TraceContextOrSamplingFlags extract(C carrier) {
    TraceContextOrSamplingFlags result = delegate.extract(carrier);

    String maybeValue = getter.get(carrier, samplingKey);
    SecondarySampling.Extra extra = new SecondarySampling.Extra();
    TraceContextOrSamplingFlags.Builder builder = result.toBuilder().addExtra(extra);
    if (maybeValue == null) return builder.build();

    for (String entry : maybeValue.split(",", 100)) {
      String[] nameParameters = entry.split(";", 100);
      String samplingKey = nameParameters[0];

      Map<String, String> parameters = nameParameters.length > 1
        ? parseParameters(nameParameters)
        : new LinkedHashMap<>();
      if (updateparametersAndSample(samplingKey, parameters)) {
        extra.sampledKeys.add(samplingKey);
        builder.sampledLocal(); // this means data will be recorded
      }
      extra.samplingKeyToParameters.put(samplingKey, parameters);
    }

    return builder.build();
  }

  boolean updateparametersAndSample(String samplingKey, Map<String, String> parameters) {
    boolean sampled = false;

    // decrement ttl from upstream, if there is one
    int ttl = 0;
    if (parameters.containsKey("ttl")) {
      ttl = Integer.parseInt(parameters.remove("ttl")) - 1;
      sampled = true;
    }

    // Lookup if our node should participate in this sampling policy
    Trigger trigger = policy.getTriggerForService(samplingKey, localServiceName);
    if (trigger != null) {
      // make a new sampling decision
      sampled = trigger.isSampled();

      // establish or refresh TTL
      if (trigger.ttl != 0) ttl = trigger.ttl;
    }

    // Add any TTL
    if (sampled && ttl != 0) parameters.put("ttl", Integer.toString(ttl));

    return sampled;
  }
}

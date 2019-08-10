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

import brave.internal.Nullable;
import brave.sampler.RateLimitingSampler;
import brave.sampler.Sampler;
import java.util.LinkedHashMap;
import java.util.Map;

final class SecondarySamplingPolicy {
  static final class Trigger {
    Sampler sampler = Sampler.ALWAYS_SAMPLE;
    int ttl = 0; // zero means don't add ttl

    boolean isSampled() {
      return sampler.isSampled(0L);
    }

    Trigger rps(int rps) {
      this.sampler = RateLimitingSampler.create(rps);
      return this;
    }

    Trigger ttl(int ttl) {
      this.ttl = ttl;
      return this;
    }
  }

  /** Sampling rules are pushed by config management to all nodes, looked up by sampling key */
  final Map<String, Trigger> allServices = new LinkedHashMap<>();
  final Map<String, Map<String, Trigger>> byService = new LinkedHashMap<>();

  SecondarySamplingPolicy addTrigger(String samplingKey, Trigger trigger) {
    allServices.put(samplingKey, trigger);
    return this;
  }

  SecondarySamplingPolicy addTrigger(String samplingKey, String serviceName, Trigger trigger) {
    getByService(samplingKey).put(serviceName, trigger);
    return this;
  }

  SecondarySamplingPolicy removeTriggers(String samplingKey) {
    allServices.remove(samplingKey);
    byService.remove(samplingKey);
    return this;
  }

  SecondarySamplingPolicy merge(SecondarySamplingPolicy input) {
    allServices.putAll(input.allServices);
    byService.putAll(input.byService);
    return this;
  }

  @Nullable Trigger getTriggerForService(String samplingKey, String serviceName) {
    Trigger result = getByService(samplingKey).get(serviceName);
    return result != null ? result : allServices.get(samplingKey);
  }

  Map<String, Trigger> getByService(String samplingKey) {
    return byService.computeIfAbsent(samplingKey, k -> new LinkedHashMap<>());
  }
}

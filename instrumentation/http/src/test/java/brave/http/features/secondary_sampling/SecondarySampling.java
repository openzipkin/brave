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

import brave.Tracing;
import brave.TracingCustomizer;
import brave.propagation.Propagation;
import brave.propagation.Propagation.KeyFactory;
import brave.propagation.TraceContext;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This is a <a href="https://github.com/openzipkin/openzipkin.github.io/wiki/secondary-sampling">Secondary
 * Sampling</a> proof of concept.
 */
final class SecondarySampling extends Propagation.Factory implements TracingCustomizer {
  static final String FIELD_NAME = "sampling";

  final String localServiceName;
  final Propagation.Factory delegate;
  final SecondarySamplingPolicy policy;

  SecondarySampling(String localServiceName, Propagation.Factory propagationFactory,
    SecondarySamplingPolicy policy) {
    this.localServiceName = localServiceName;
    this.delegate = propagationFactory;
    this.policy = policy;
  }

  @Override public boolean supportsJoin() {
    return delegate.supportsJoin();
  }

  @Override public <K> Propagation<K> create(KeyFactory<K> keyFactory) {
    if (keyFactory == null) throw new NullPointerException("keyFactory == null");
    return new Propagation<>(delegate.create(keyFactory), keyFactory.create(FIELD_NAME), this);
  }

  @Override public boolean requires128BitTraceId() {
    return delegate.requires128BitTraceId();
  }

  @Override public TraceContext decorate(TraceContext context) {
    return delegate.decorate(context);
  }

  @Override public void customize(Tracing.Builder builder) {
    builder.addFinishedSpanHandler(new SecondarySamplingFinishedSpanHandler())
      .propagationFactory(this)
      .alwaysReportSpans();
  }

  /** This state is added to every span. A real implementation would be memory conscious. */
  static final class Extra {
    final Map<String, Map<String, String>> samplingKeyToParameters = new LinkedHashMap<>();
    final Set<String> sampledKeys = new LinkedHashSet<>();
  }

  static class Propagation<K> implements brave.propagation.Propagation<K> {
    final brave.propagation.Propagation<K> delegate;
    final K samplingKey;
    final SecondarySampling secondarySampling;

    Propagation(brave.propagation.Propagation<K> delegate, K samplingKey,
      SecondarySampling secondarySampling) {
      this.delegate = delegate;
      this.samplingKey = samplingKey;
      this.secondarySampling = secondarySampling;
    }

    @Override public List<K> keys() {
      return delegate.keys();
    }

    @Override public <C> TraceContext.Injector<C> injector(Setter<C, K> setter) {
      if (setter == null) throw new NullPointerException("setter == null");
      return new SecondarySamplingInjector<>(this, setter);
    }

    @Override public <C> TraceContext.Extractor<C> extractor(Getter<C, K> getter) {
      if (getter == null) throw new NullPointerException("getter == null");
      return new SecondarySamplingExtractor<>(this, getter);
    }
  }

  /** Like Accept header, except that we are ignoring the name and only returning parameters. */
  static Map<String, String> parseParameters(String[] nameParameters) {
    Map<String, String> result = new LinkedHashMap<>();
    for (int i = 1; i < nameParameters.length; i++) {
      String[] nameValue = nameParameters[i].split("=", 2);
      result.put(nameValue[0], nameValue[1]);
    }
    return result;
  }
}

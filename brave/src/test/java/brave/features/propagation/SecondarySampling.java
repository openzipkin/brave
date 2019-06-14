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
package brave.features.propagation;

import brave.Tracing;
import brave.handler.MutableSpan;
import brave.propagation.B3SinglePropagation;
import brave.propagation.Propagation.Getter;
import brave.propagation.Propagation.KeyFactory;
import brave.propagation.Propagation.Setter;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.sampler.RateLimitingSampler;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class SecondarySampling {

  public static SecondarySampling create() {
    return new SecondarySampling();
  }

  Propagation.Factory propagationFactory = B3SinglePropagation.FACTORY;
  final Map<String, brave.handler.FinishedSpanHandler> systemToHandlers = new ConcurrentHashMap<>();

  SecondarySampling() {
  }

  public SecondarySampling putSystem(String system, brave.handler.FinishedSpanHandler handler) {
    systemToHandlers.put(system, handler);
    return this;
  }

  public SecondarySampling removeSystem(String system) {
    systemToHandlers.remove(system);
    return this;
  }

  public Tracing build(Tracing.Builder builder) {
    return builder
      .addFinishedSpanHandler(new FinishedSpanHandler(systemToHandlers))
      // BRAVE6: we need a better collaboration model for propagation than wrapping, as it makes
      // configuration complex.
      .propagationFactory(new PropagationFactory(propagationFactory, systemToHandlers.keySet()))
      .build();
  }

  static final class FinishedSpanHandler extends brave.handler.FinishedSpanHandler {
    final Map<String, brave.handler.FinishedSpanHandler> systemToHandlers;

    FinishedSpanHandler(Map<String, brave.handler.FinishedSpanHandler> systemToHandlers) {
      this.systemToHandlers = systemToHandlers;
    }

    @Override public boolean handle(TraceContext context, MutableSpan span) {
      Extra extra = context.findExtra(Extra.class);
      if (extra == null) return true;
      for (String system : extra.systems.keySet()) {
        brave.handler.FinishedSpanHandler handler = systemToHandlers.get(system);
        if (handler != null && "1".equals(extra.systems.get(system).get("sampled"))) {
          handler.handle(context, span);
        }
      }
      return true;
    }
  }

  static final class PropagationFactory extends Propagation.Factory {
    final Propagation.Factory delegate;
    final Set<String> configuredSystems;

    PropagationFactory(Propagation.Factory delegate, Set<String> configuredSystems) {
      this.delegate = delegate;
      this.configuredSystems = configuredSystems;
    }

    @Override public boolean supportsJoin() {
      return delegate.supportsJoin();
    }

    @Override public <K> Propagation<K> create(KeyFactory<K> keyFactory) {
      return new Propagation<>(delegate.create(keyFactory), keyFactory, configuredSystems);
    }
  }

  static final class Extra {
    final Map<String, Map<String, String>> systems = new LinkedHashMap<>();

    @Override public String toString() {
      return systems.entrySet()
        .stream()
        .map(s -> s.getKey() + ":" + Extra.toString(s.getValue()))
        .collect(Collectors.joining(";"));
    }

    static String toString(Map<String, String> s) {
      return s.entrySet()
        .stream()
        .map(e -> e.getKey() + "=" + e.getValue())
        .collect(Collectors.joining(","));
    }
  }

  static class Propagation<K> implements brave.propagation.Propagation<K> {
    final brave.propagation.Propagation<K> delegate;
    final Set<String> configuredSystems;
    final K samplingKey;

    Propagation(brave.propagation.Propagation<K> delegate, KeyFactory<K> keyFactory,
      Set<String> configuredSystems) {
      this.delegate = delegate;
      this.configuredSystems = configuredSystems;
      this.samplingKey = keyFactory.create("sampling");
    }

    @Override public List<K> keys() {
      return delegate.keys();
    }

    @Override public <C> TraceContext.Injector<C> injector(Setter<C, K> setter) {
      if (setter == null) throw new NullPointerException("setter == null");
      return new Injector<>(this, setter);
    }

    @Override public <C> TraceContext.Extractor<C> extractor(Getter<C, K> getter) {
      return new Extractor<>(this, getter);
    }
  }

  static final class Injector<C, K> implements TraceContext.Injector<C> {
    final TraceContext.Injector<C> delegate;
    final Setter<C, K> setter;
    final K samplingKey;

    Injector(Propagation<K> propagation, brave.propagation.Propagation.Setter<C, K> setter) {
      this.delegate = propagation.delegate.injector(setter);
      this.setter = setter;
      this.samplingKey = propagation.samplingKey;
    }

    @Override public void inject(TraceContext traceContext, C carrier) {
      delegate.inject(traceContext, carrier);
      Extra sampled = traceContext.findExtra(Extra.class);
      if (sampled == null || sampled.systems.isEmpty()) return;
      setter.put(carrier, samplingKey, sampled.toString());
    }
  }

  static final class Extractor<C, K> implements TraceContext.Extractor<C> {
    final TraceContext.Extractor<C> delegate;
    final Getter<C, K> getter;
    final Set<String> configuredSystems;
    final K samplingKey;

    Extractor(Propagation<K> propagation, Getter<C, K> getter) {
      this.delegate = propagation.delegate.extractor(getter);
      this.getter = getter;
      this.configuredSystems = propagation.configuredSystems;
      this.samplingKey = propagation.samplingKey;
    }

    @Override public TraceContextOrSamplingFlags extract(C carrier) {
      TraceContextOrSamplingFlags result = delegate.extract(carrier);

      String maybeValue = getter.get(carrier, samplingKey);
      Extra extra = new Extra();
      TraceContextOrSamplingFlags.Builder builder = result.toBuilder().addExtra(extra);
      if (maybeValue == null) return builder.build();

      for (String entry : maybeValue.split(";", 100)) {
        String[] nameValue = entry.split(":", 2);
        String name = nameValue[0];

        Map<String, String> systemToState = parseSystem(nameValue[1]);
        if (configuredSystems.contains(name) && updateStateAndSample(systemToState)) {
          builder.sampledLocal(); // this means data will be recorded
        }
        if (!systemToState.isEmpty()) extra.systems.put(name, systemToState);
      }

      return builder.build();
    }
  }

  static Map<String, String> parseSystem(String system) {
    Map<String, String> result = new LinkedHashMap<>();
    for (String entry : system.split(",", 100)) {
      String[] nameValue = entry.split("=", 2);
      result.put(nameValue[0], nameValue[1]);
    }
    return result;
  }

  static boolean updateStateAndSample(Map<String, String> state) {
    // if there's a tps, convert it to a sampling decision
    String tps = state.remove("tps");
    if (tps != null) {
      // in real life the sampler would be cached
      boolean decision = RateLimitingSampler.create(Integer.parseInt(tps)).isSampled(1L);
      state.put("sampled", decision ? "1" : "0");
      return decision;
    }

    if (state.containsKey("ttl")) { // decrement ttl if there is one
      String ttl = state.remove("ttl");
      if (ttl.equals("1")) {
        state.remove("sampled");
      } else {
        state.put("ttl", Integer.toString(Integer.parseInt(ttl) - 1));
      }
    }
    return "1".equals(state.get("sampled"));
  }
}

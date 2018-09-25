package brave.features.propagation;

import brave.handler.MutableSpan;
import brave.propagation.Propagation.Getter;
import brave.propagation.Propagation.KeyFactory;
import brave.propagation.Propagation.Setter;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.sampler.Sampler;
import com.google.common.base.Splitter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class SecondarySampling {
  public static final class FinishedSpanHandler extends brave.handler.FinishedSpanHandler {
    final Map<String, brave.handler.FinishedSpanHandler> configuredHandlers;

    public FinishedSpanHandler(Map<String, brave.handler.FinishedSpanHandler> configuredHandlers) {
      this.configuredHandlers = configuredHandlers;
    }

    @Override public boolean handle(TraceContext context, MutableSpan span) {
      Extra extra = context.findExtra(Extra.class);
      if (extra == null) return true;
      for (String state : extra.states.keySet()) {
        brave.handler.FinishedSpanHandler handler = configuredHandlers.get(state);
        if (handler != null) handler.handle(context, span);
      }
      return true;
    }
  }

  public static final class PropagationFactory extends Propagation.Factory {
    final Propagation.Factory delegate;
    final Set<String> configuredStates;

    PropagationFactory(Propagation.Factory delegate, Set<String> configuredStates) {
      this.delegate = delegate;
      this.configuredStates = configuredStates;
    }

    @Override public boolean supportsJoin() {
      return delegate.supportsJoin();
    }

    @Override public <K> Propagation<K> create(KeyFactory<K> keyFactory) {
      return new Propagation<>(delegate.create(keyFactory), keyFactory, configuredStates);
    }
  }

  static final class Extra {
    Map<String, Map<String, String>> states = new LinkedHashMap<>();

    @Override public String toString() {
      return states.entrySet()
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
    final Set<String> configuredStates;
    final K samplingKey;

    Propagation(brave.propagation.Propagation<K> delegate, KeyFactory<K> keyFactory,
        Set<String> configuredStates) {
      this.delegate = delegate;
      this.configuredStates = configuredStates;
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
      if (sampled == null || sampled.states.isEmpty()) return;
      setter.put(carrier, samplingKey, sampled.toString());
    }
  }

  static final class Extractor<C, K> implements TraceContext.Extractor<C> {
    final TraceContext.Extractor<C> delegate;
    final Getter<C, K> getter;
    final Set<String> configuredStates;
    final K samplingKey;

    Extractor(Propagation<K> propagation, Getter<C, K> getter) {
      this.delegate = propagation.delegate.extractor(getter);
      this.getter = getter;
      this.configuredStates = propagation.configuredStates;
      this.samplingKey = propagation.samplingKey;
    }

    @Override public TraceContextOrSamplingFlags extract(C carrier) {
      TraceContextOrSamplingFlags result = delegate.extract(carrier);

      String maybeValue = getter.get(carrier, samplingKey);
      Extra extra = new Extra();
      TraceContextOrSamplingFlags.Builder builder = result.toBuilder().addExtra(extra);
      if (maybeValue == null) return builder.build();

      for (String entry : Splitter.on(";").split(maybeValue)) {
        String[] nameValue = entry.split(":");
        String name = nameValue[0];
        Map<String, String> state = Splitter.on(",").withKeyValueSeparator("=").split(nameValue[1]);

        if (configuredStates.contains(name)) {
          state = new LinkedHashMap<>(state); // make mutable
          if (update(state)) {
            if (state.get("sampled").equals("1")) {
              builder.sampledLocal(); // this allows us to override the default decision
            }
            extra.states.put(name, state);
          }
        } else {
          extra.states.put(name, state);
        }
      }

      return builder.build();
    }
  }

  static boolean update(Map<String, String> state) {
    // if there's a rate, convert it to a sampling decision
    String rate = state.remove("rate");
    if (rate != null) {
      // in real life the sampler would be cached
      boolean decision = Sampler.create(Float.parseFloat(rate)).isSampled(1L);
      state.put("sampled", decision ? "1" : "0");
    } else if (state.containsKey("ttl")) {
      // decrement ttl if there is one
      String ttl = state.remove("ttl");
      if (ttl != null && !ttl.equals("1")) {
        state.put("ttl", Integer.toString(Integer.parseInt(ttl) - 1));
      } else {
        return false; // remove the out-dated decision
      }
    }
    return true;
  }
}

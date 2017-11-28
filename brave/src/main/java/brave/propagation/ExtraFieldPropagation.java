package brave.propagation;

import brave.Tracing;
import brave.internal.Nullable;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Allows you to propagate predefined request-scoped fields, usually but not always HTTP headers.
 *
 * <p>For example, if you are in a Cloud Foundry environment, you might want to pass the request
 * ID:
 * <pre>{@code
 * // when you initialize the builder, define the extra field you want to propagate
 * tracingBuilder.propagationFactory(
 *   ExtraFieldPropagation.newFactory(B3Propagation.FACTORY, "x-vcap-request-id")
 * );
 *
 * // later, you can tag that request ID or use it in log correlation
 * requestId = ExtraFieldPropagation.current("x-vcap-request-id");
 * }</pre>
 *
 * <p>You may also need to propagate a trace context you aren't using. For example, you may be in an
 * Amazon Web Services environment, but not reporting data to X-Ray. To ensure X-Ray can co-exist
 * correctly, pass-through its tracing header like so.
 *
 * <pre>{@code
 * tracingBuilder.propagationFactory(
 *   ExtraFieldPropagation.newFactory(B3Propagation.FACTORY, "x-amzn-trace-id")
 * );
 * }</pre>
 */
public final class ExtraFieldPropagation<K> implements Propagation<K> {

  /** Wraps an underlying propagation implementation, pushing one or more fields */
  public static Propagation.Factory newFactory(Propagation.Factory delegate, String... names) {
    return new Factory(delegate, Arrays.asList(names));
  }

  /** Wraps an underlying propagation implementation, pushing one or more fields */
  public static Propagation.Factory newFactory(Propagation.Factory delegate, Collection<String> names) {
    return new Factory(delegate, names);
  }

  /** Returns the value of the field with the specified key or null if not available */
  @Nullable public static String current(String name) {
    Tracing tracing = Tracing.current();
    if (tracing == null) return null;
    TraceContext context = tracing.currentTraceContext().get();
    if (context == null) return null;
    return get(context, name);
  }

  /** Returns the value of the field with the specified key or null if not available */
  @Nullable public static String get(TraceContext context, String name) {
    if (context == null) throw new NullPointerException("context == null");
    if (name == null) throw new NullPointerException("name == null");
    name = name.toLowerCase(Locale.ROOT); // since not all propagation handle mixed case
    for (Object extra : context.extra()) {
      if (extra instanceof Extra) return ((Extra) extra).get(name);
    }
    return null;
  }

  static final class Factory extends Propagation.Factory {
    final Propagation.Factory delegate;
    final List<String> names;

    Factory(Propagation.Factory delegate, Collection<String> names) {
      if (delegate == null) throw new NullPointerException("field == null");
      if (names == null) throw new NullPointerException("names == null");
      if (names.isEmpty()) throw new NullPointerException("names.length == 0");
      this.delegate = delegate;
      this.names = new ArrayList<>();
      for (String name : names) {
        this.names.add(name.toLowerCase(Locale.ROOT));
      }
    }

    @Override public boolean supportsJoin() {
      return delegate.supportsJoin();
    }

    @Override public boolean requires128BitTraceId() {
      return delegate.requires128BitTraceId();
    }

    @Override public final <K> Propagation<K> create(Propagation.KeyFactory<K> keyFactory) {
      Map<String, K> names = new LinkedHashMap<>();
      for (String name : this.names) {
        names.put(name, keyFactory.create(name));
      }
      return new ExtraFieldPropagation<>(delegate.create(keyFactory), names);
    }
  }

  final Propagation<K> delegate;
  final List<K> keys;
  final Map<String, K> nameToKey;

  ExtraFieldPropagation(Propagation<K> delegate, Map<String, K> nameToKey) {
    this.delegate = delegate;
    this.nameToKey = nameToKey;
    List<K> keys = new ArrayList<>(delegate.keys());
    keys.addAll(nameToKey.values());
    this.keys = Collections.unmodifiableList(keys);
  }

  @Override public List<K> keys() {
    return keys;
  }

  @Override public <C> Injector<C> injector(Setter<C, K> setter) {
    return new ExtraFieldInjector<>(delegate.injector(setter), setter, nameToKey);
  }

  @Override public <C> Extractor<C> extractor(Getter<C, K> getter) {
    Extractor<C> extractorDelegate = delegate.extractor(getter);
    return new ExtraFieldExtractor<>(extractorDelegate, getter, nameToKey);
  }

  static abstract class Extra { // internal marker type
    abstract void put(String field, String value);

    abstract String get(String field);

    abstract <C, K> void setAll(C carrier, Setter<C, K> setter, Map<String, K> nameToKey);
  }

  static final class One extends Extra {
    String name, value;

    @Override void put(String name, String value) {
      this.name = name;
      this.value = value;
    }

    @Override String get(String name) {
      return name.equals(this.name) ? value : null;
    }

    @Override <C, K> void setAll(C carrier, Setter<C, K> setter, Map<String, K> nameToKey) {
      K key = nameToKey.get(name);
      if (key == null) return;
      setter.put(carrier, key, value);
    }

    @Override public String toString() {
      return "ExtraFieldPropagation{" + name + "=" + value + "}";
    }
  }

  static final class Many extends Extra {
    final LinkedHashMap<String, String> fields = new LinkedHashMap<>();

    @Override void put(String name, String value) {
      fields.put(name, value);
    }

    @Override String get(String name) {
      return fields.get(name);
    }

    @Override <C, K> void setAll(C carrier, Setter<C, K> setter, Map<String, K> nameToKey) {
      for (Map.Entry<String, String> field : fields.entrySet()) {
        K key = nameToKey.get(field.getKey());
        if (key == null) continue;
        setter.put(carrier, nameToKey.get(field.getKey()), field.getValue());
      }
    }

    @Override public String toString() {
      return "ExtraFieldPropagation" + fields;
    }
  }

  static final class ExtraFieldInjector<C, K> implements Injector<C> {
    final Injector<C> delegate;
    final Propagation.Setter<C, K> setter;
    final Map<String, K> nameToKey;

    ExtraFieldInjector(Injector<C> delegate, Setter<C, K> setter, Map<String, K> nameToKey) {
      this.delegate = delegate;
      this.setter = setter;
      this.nameToKey = nameToKey;
    }

    @Override public void inject(TraceContext traceContext, C carrier) {
      for (Object extra : traceContext.extra()) {
        if (extra instanceof Extra) {
          ((Extra) extra).setAll(carrier, setter, nameToKey);
          break;
        }
      }
      delegate.inject(traceContext, carrier);
    }
  }

  static final class ExtraFieldExtractor<C, K> implements Extractor<C> {
    final Extractor<C> delegate;
    final Propagation.Getter<C, K> getter;
    final Map<String, K> names;

    ExtraFieldExtractor(Extractor<C> delegate, Getter<C, K> getter, Map<String, K> names) {
      this.delegate = delegate;
      this.getter = getter;
      this.names = names;
    }

    @Override public TraceContextOrSamplingFlags extract(C carrier) {
      TraceContextOrSamplingFlags result = delegate.extract(carrier);

      Extra extra = null;
      for (Map.Entry<String, K> field : names.entrySet()) {
        String maybeValue = getter.get(carrier, field.getValue());
        if (maybeValue == null) continue;
        if (extra == null) {
          extra = new One();
        } else if (extra instanceof One) {
          One one = (One) extra;
          extra = new Many();
          extra.put(one.name, one.value);
        }
        extra.put(field.getKey(), maybeValue);
      }
      if (extra == null) return result;
      return result.toBuilder().addExtra(extra).build();
    }
  }
}
